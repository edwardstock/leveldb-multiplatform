package com.edwardstock.leveldb.internal

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.LevelDBInstance.Companion.ensureOpen
import com.edwardstock.leveldb.api.getValue
import com.edwardstock.leveldb.api.putValue
import com.edwardstock.leveldb.api.set
import com.edwardstock.leveldb.config.createDB
import com.edwardstock.leveldb.exception.LevelDBCorruptedMigrationException
import com.edwardstock.leveldb.exception.LevelDBMigrationException
import com.edwardstock.leveldb.log.LevelDBLogger
import com.edwardstock.leveldb.migration.LevelDBMigration
import com.edwardstock.leveldb.migration.LevelDBMigrationSafetyPolicy
import com.edwardstock.leveldb.migration.LevelDBSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path

internal interface LevelDBMigrator {
    suspend fun run()
}

internal class LevelDBMigratorImpl(
    private val instance: LevelDBInstance,
) : LevelDBMigrator {
    private val state: LevelDBEntryState get() = instance.state
    private val schema: LevelDBSchema get() = checkNotNull(instance.schema)
    private val logger: LevelDBLogger get() = instance.config.requireLogger

    override suspend fun run() {
        instance.schema ?: return

        state.self.withLock {
            if (state.schemaFailedState != null) {
                throw LevelDBCorruptedMigrationException("Cannot migrate to ${schema.targetVersion} - it has already failed")
            }
        }

        if (state.schemaOkForVersion == schema.targetVersion) return

        val db = state.self.withLock {
            requireNotNull(state.db) { "LevelDB not opened for ${state.path}" }
        }
        val storedVersion: Int = db.getValue(schema.versionKey) ?: 0

        if (storedVersion == schema.targetVersion) {
            state.self.withLock {
                state.schemaOkForVersion = schema.targetVersion
            }
            return
        }

        if (storedVersion > schema.targetVersion && !schema.allowDowngrade) {
            throw LevelDBMigrationException(
                "Schema downgrade is not allowed: stored=$storedVersion, target=${schema.targetVersion}"
            )
        }

        logger.logD("Migration: Migrating DB at ${state.path} from version $storedVersion to ${schema.targetVersion}")

        // close before migration
        state.self.withLock {
            db.close().also {
                state.db = null
            }
        }

        when (schema.safety) {
            LevelDBMigrationSafetyPolicy.NONE -> {
                logger.logD("Migration: migrate in-place")
                migrateInPlace(state.pathOkio)
            }

            LevelDBMigrationSafetyPolicy.BACKUP_DIR -> {
                val backup = backupPath(state.pathOkio)
                instance.fileSystem.deleteRecursivelyIfExists(backup)
                instance.fileSystem.copyRecursively(state.pathOkio, backup)

                logger.logD("Migration: migrate with making backup (backup=$backup)")

                runCatching {
                    migrateInPlace(state.pathOkio)
                }.onFailure { error ->
                    // restore backup
                    runCatching { instance.fileSystem.deleteRecursivelyIfExists(state.pathOkio) }
                    runCatching { instance.fileSystem.moveDir(backup, state.pathOkio) }
                    logger.logE(error, "Failed to migrate with backup; backup is restored")

                    throw LevelDBMigrationException("Migration failed, restored from backup", error)
                }.getOrThrow()

                instance.fileSystem.deleteRecursivelyIfExists(backup)
            }

            LevelDBMigrationSafetyPolicy.STAGING_DB -> {
                val staging = stagingPath(state.pathOkio)
                val backup = backupPath(state.pathOkio)

                logger.logD("Migration: migrate to staging dir (staging=$staging, backup=$backup)")

                instance.fileSystem.deleteRecursivelyIfExists(staging)
                instance.fileSystem.copyRecursively(state.pathOkio, staging)

                // migrate staging copy
                migrateInPlace(staging)

                // swap dirs
                instance.fileSystem.deleteRecursivelyIfExists(backup)
                instance.fileSystem.moveDir(state.pathOkio, backup)
                runCatching {
                    instance.fileSystem.moveDir(staging, state.pathOkio)
                }.onFailure { error ->
                    // rollback swap
                    runCatching { instance.fileSystem.deleteRecursivelyIfExists(state.pathOkio) }
                    runCatching { instance.fileSystem.moveDir(backup, state.pathOkio) }

                    logger.logE(error, "Failed to migrate with backup; backup is restored")

                    throw LevelDBMigrationException("Swap failed, restored original database", error)
                }.getOrThrow()

                instance.fileSystem.deleteRecursivelyIfExists(backup)
                instance.fileSystem.deleteRecursivelyIfExists(staging)
            }
        }

        logger.logD("Migration: reopen the instance")
        ensureOpen(
            entry = state,
            config = instance.config,
        )
        state.self.withLock {
            state.schemaOkForVersion = schema.targetVersion
            // cleanup previously failed migration state if any
            state.schemaFailedState = null
            logger.logD("Migration: migration succeeded schemaOkForVersion=${schema.targetVersion}")
        }
    }

    private suspend fun migrateInPlace(
        path: Path
    ) {
        val migDb = instance.config.createDB(path.toString())
        var storedVersion: Int = -1
        var inProgress: Int? = null
        try {
            storedVersion = migDb.getValue<Int>(schema.versionKey) ?: 0
            inProgress = migDb.getValue<Int>(schema.inProgressKey)

            if (storedVersion == schema.targetVersion) {
                // if someone left garbage inProgressKey - clean it up
                migDb[schema.inProgressKey] = null
                return
            }

            if (storedVersion > schema.targetVersion && !schema.allowDowngrade) {
                throw LevelDBMigrationException(
                    "Schema downgrade is not allowed: stored=$storedVersion, target=${schema.targetVersion}"
                )
            }

            val plan = resolveLinearPlanOrThrow(
                from = storedVersion,
                to = schema.targetVersion,
                migrations = schema.migrations,
            )

            // validate inProgress semantics
            if (inProgress != null) {
                // normal crash scenario:
                // - migration step k->k+1 started, set inProgress=k+1
                // - it crashed before versionKey got updated
                // next run: storedVersion == k, inProgress == k+1 => we retry that step
                val expected = storedVersion + 1
                if (inProgress != expected && inProgress != schema.targetVersion) {
                    throw LevelDBMigrationException(
                        "Invalid inProgress state: storedVersion=$storedVersion, inProgress=$inProgress. " +
                                "Expected $expected (retry current step) or ${schema.targetVersion} (global target marker)."
                    )
                }
            }

            logger.logD(
                "Migration: stored=$storedVersion target=${schema.targetVersion} " +
                        "inProgress=${inProgress ?: "null"} steps=${plan.size}"
            )

            for (migration in plan) {
                // mark that we are attempting to reach `migration.to`
                migDb.putValue(schema.inProgressKey, migration.to)

                try {
                    migration.migrate(migDb)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.logE(e, "Failed to run migration step: ${migration.name} (${migration.from} -> ${migration.to})")

                    instance.state.self.withLock {
                        instance.state.schemaFailedState = LevelDBSchemaFailedState.createFailure(schema, migration.from, migration.to)
                    }

                    throw LevelDBMigrationException(
                        message = "Migration step failed: ${migration.name} (${migration.from} -> ${migration.to})",
                        cause = e,
                    )
                }

                // step succeeded: bump version
                migDb[schema.versionKey] = migration.to
            }

            migDb.del(schema.inProgressKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LevelDBMigrationException) {
            logger.logE(e, "Failed to migrate DB at $path to ${schema.targetVersion}")
            throw e
        } catch (e: Throwable) {
            logger.logE(e, "Something went wrong during DB migration at $path to ${schema.targetVersion}")
            state.self.withLock {
                state.schemaFailedState = LevelDBSchemaFailedState.createFailure(
                    schema = schema,
                    fromVersion = storedVersion,
                    inProgressVersion = inProgress,
                )
            }
            throw e
        } finally {
            runCatching { migDb.close() }
        }
    }

    private fun resolveLinearPlanOrThrow(
        from: Int,
        to: Int,
        migrations: List<LevelDBMigration>,
    ): List<LevelDBMigration> {
        if (from == to) return emptyList()
        if (from > to) throw LevelDBMigrationException("Downgrade plan is not supported by linear migrations: $from -> $to")

        // enforce - exactly one migration per "from", and `it` must be from->from+1
        val byFrom = migrations.groupBy { it.from }
        val plan = ArrayList<LevelDBMigration>(to - from)

        var cur = from
        while (cur < to) {
            val candidates = byFrom[cur].orEmpty()

            if (candidates.isEmpty()) {
                throw LevelDBMigrationException(
                    "Missing migration $cur -> ${cur + 1}. " +
                            "Add a real migration or SoftMigration($cur, ${cur + 1})."
                )
            }
            if (candidates.size != 1) {
                throw LevelDBMigrationException(
                    "Ambiguous migrations for version $cur: found ${candidates.size}. " +
                            "Linear history requires exactly one migration per step."
                )
            }

            val m = candidates.single()
            if (m.to != cur + 1) {
                throw LevelDBMigrationException(
                    "Non-linear migration is not allowed: ${m.from} -> ${m.to}. " +
                            "Only $cur -> ${cur + 1} is permitted."
                )
            }

            plan += m
            cur = m.to
        }

        return plan
    }

    private fun backupPath(dbPath: Path): Path {
        val parent = requireNotNull(dbPath.parent) { "Database path must have a parent: $dbPath" }
        return parent / (dbPath.name + schema.backupDirName)
    }

    private fun stagingPath(dbPath: Path): Path {
        val parent = requireNotNull(dbPath.parent) { "Database path must have a parent: $dbPath" }
        return parent / (dbPath.name + schema.stagingDirName)
    }

    private fun FileSystem.deleteRecursivelyIfExists(path: Path) {
        if (!exists(path)) return
        val meta = metadata(path)
        if (meta.isDirectory) {
            list(path).forEach { child ->
                deleteRecursivelyIfExists(child)
            }
        }
        delete(path)
    }

    private fun FileSystem.copyRecursively(from: Path, to: Path) {
        val meta = metadata(from)
        if (!meta.isDirectory) {
            copy(from, to)
            return
        }

        if (!exists(to)) {
            createDirectory(to)
        }

        list(from).forEach { child ->
            val target = to / child.name
            copyRecursively(child, target)
        }
    }

    private fun FileSystem.moveDir(source: Path, target: Path) {
        runCatching {
            atomicMove(source, target)
        }.getOrElse {
            // Fallback if atomicMove is not supported by the underlying FS
            copyRecursively(source, target)
            deleteRecursivelyIfExists(source)
        }
    }
}
