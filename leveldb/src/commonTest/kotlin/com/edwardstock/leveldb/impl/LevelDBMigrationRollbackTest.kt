/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.getString
import com.edwardstock.leveldb.api.getValue
import com.edwardstock.leveldb.api.open
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.api.putValue
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.exception.LevelDBMigrationException
import com.edwardstock.leveldb.migration.LevelDBMigration
import com.edwardstock.leveldb.migration.LevelDBMigrationSafetyPolicy
import com.edwardstock.leveldb.migration.LevelDBSchema
import com.edwardstock.leveldb.migration.LevelDBSchemaVersion
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T4: rollback behavior of [com.edwardstock.leveldb.internal.LevelDBMigratorImpl] under the
 * recoverable safety policies. The pre-existing [LevelDBMigrationTest] only exercises:
 *   - the happy path for BACKUP_DIR / STAGING_DB (migration succeeds), and
 *   - the NONE policy on failure (no rollback: partial writes survive).
 *
 * These tests pin the *failure* arms of the BACKUP_DIR and STAGING_DB branches:
 *   - a failing migration step must restore the pre-migration database, and
 *   - both the committed-step data AND the failing step's partial write must be gone
 *     (the exact opposite of the NONE contract), and
 *   - the backup / staging scratch directories must not be left behind.
 *
 * Strategy: a *schema-less* [LevelDBInstance] seeds the on-disk DB with version 0 plus a
 * user key (the "pre-migration" data) before the migrator ever runs. Then a managed instance
 * with the recoverable schema runs a 0 -> 2 migration whose second step throws. We read the
 * resulting on-disk state through a *raw* [LevelDB] handle because the managed instance is
 * poisoned (schemaFailedState set) and its `use {}` would re-trigger the failing migrator.
 */
class LevelDBMigrationRollbackTest {

    // ---------------------------------------------------------------------------------------------
    // BACKUP_DIR: copy path -> backup, migrate in place, on failure restore backup -> path.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `BACKUP_DIR rolls back to pre-migration data when a step fails`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()

        // Pre-migration state: version 0 and one user entry whose value is DISTINCT from its key,
        // so a mutation that resurrects stale/garbage data instead of the genuine original is caught.
        seedPreMigration(path)

        val executed = mutableListOf<String>()
        val schema = LevelDBSchema(
            targetVersion = 2,
            safety = LevelDBMigrationSafetyPolicy.BACKUP_DIR,
            migrations = listOf(
                // Step 0->1 commits: writes "committed" and durably bumps version to 1.
                KvMigration(0, 1, "committed", "committed#val", executed),
                // Step 1->2 writes a partial marker then throws: this is where rollback must engage.
                FailingKvMigration(1, 2, "m1_2", executed),
            ),
        )
        val instance = migrationInstance(path, schema)

        try {
            // The BACKUP_DIR failure arm wraps migrateInPlace in runCatching, restores the backup,
            // and rethrows as a LevelDBMigrationException carrying the "restored from backup" message.
            // A mutant that drops the runCatching/onFailure restore would surface the inner
            // RuntimeException("boom...") instead (or leave the half-migrated DB in place).
            val ex = assertFailsWith<LevelDBMigrationException> {
                instance.use { putString("x", "y") }
            }
            assertTrue(
                ex.message?.contains("restored from backup", ignoreCase = true) == true,
                "Expected the BACKUP_DIR restore diagnostic, got: ${ex.message}",
            )

            // Both steps were entered (step 0->1 ran fully; step 1->2 recorded before throwing).
            assertEquals(listOf("committed", "m1_2"), executed)
            // The DB is flagged failed so callers treat it as unusable.
            assertTrue(instance.state.schemaFailedState != null)

            // The load-bearing rollback assertions, read through a RAW handle (the managed instance
            // is poisoned). After restore the on-disk DB must be byte-for-byte the pre-migration DB:
            val raw = LevelDB.open(path.toString())
            try {
                // (1) Pre-migration user data is intact.
                assertEquals(
                    "original#val",
                    raw.getString("user:keep"),
                    "pre-migration data must survive the rollback",
                )
                // (2) Version was rolled back to the seeded 0, NOT left at the committed step's 1.
                //     A mutant that fails to restore the backup would leave version == 1 here.
                assertEquals(
                    0,
                    raw.getValue<Int>(schema.versionKey),
                    "version must be restored to the pre-migration value (0), not the committed step's 1",
                )
                // (3) The committed step's data is GONE — unlike NONE, BACKUP_DIR discards it on failure.
                assertNull(
                    raw.getString("committed"),
                    "committed step's write must be rolled back under BACKUP_DIR (unlike NONE)",
                )
                // (4) The failing step's partial write is GONE.
                assertNull(
                    raw.getString("preFailMarker"),
                    "failing step's partial write must be rolled back under BACKUP_DIR",
                )
                // (5) No stale crash-resume marker leaks into the restored DB.
                assertNull(
                    raw.getBytes(schema.inProgressKey),
                    "inProgress marker must not survive into the restored DB",
                )
            } finally {
                raw.close()
            }

            // The backup scratch dir must NOT be left behind: on the failure arm it is consumed by
            // the moveDir(backup -> path) restore. A mutant that copied (instead of moved) the backup
            // back, or skipped restore entirely, would leave the backup dir present.
            assertTrue(
                !FileSystem.SYSTEM.exists(backupDir(path)),
                "backup dir must not be left behind after a rollback",
            )
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
            deleteTree(backupDir(path))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // STAGING_DB: copy path -> staging, migrate the COPY, swap dirs only on success. A step failure
    // throws from migrateInPlace(staging) BEFORE any swap, so the live `path` is never touched.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `STAGING_DB leaves the live DB untouched when a step fails`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()

        seedPreMigration(path)

        val executed = mutableListOf<String>()
        val schema = LevelDBSchema(
            targetVersion = 2,
            safety = LevelDBMigrationSafetyPolicy.STAGING_DB,
            migrations = listOf(
                KvMigration(0, 1, "committed", "committed#val", executed),
                FailingKvMigration(1, 2, "m1_2", executed),
            ),
        )
        val instance = migrationInstance(path, schema)

        try {
            // The step failure propagates out of migrateInPlace(staging). It is wrapped once by
            // migrateInPlace into a LevelDBMigrationException("Migration step failed: ...").
            val ex = assertFailsWith<LevelDBMigrationException> {
                instance.use { putString("x", "y") }
            }
            assertTrue(
                ex.message?.contains("Migration step failed", ignoreCase = true) == true,
                "Expected the failing-step diagnostic from migrateInPlace, got: ${ex.message}",
            )

            assertEquals(listOf("committed", "m1_2"), executed)
            assertTrue(instance.state.schemaFailedState != null)

            // The live DB at `path` is the ORIGINAL directory — STAGING_DB migrates a copy and only
            // swaps on success, so a step failure must leave `path` byte-for-byte pre-migration.
            // A mutant that migrated the live DB in place (instead of the staging copy), or that
            // swapped the half-migrated staging dir in despite the failure, would corrupt this.
            val raw = LevelDB.open(path.toString())
            try {
                assertEquals(
                    "original#val",
                    raw.getString("user:keep"),
                    "pre-migration data must remain in the live DB (only the staging copy is migrated)",
                )
                assertEquals(
                    0,
                    raw.getValue<Int>(schema.versionKey),
                    "live DB version must stay at the pre-migration 0",
                )
                assertNull(
                    raw.getString("committed"),
                    "committed step ran only against the staging copy; the live DB must not see it",
                )
                assertNull(
                    raw.getString("preFailMarker"),
                    "failing step's partial write happened in staging, not the live DB",
                )
                assertNull(
                    raw.getBytes(schema.inProgressKey),
                    "no crash-resume marker may leak into the untouched live DB",
                )
            } finally {
                raw.close()
            }
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
            deleteTree(stagingDir(path))
            deleteTree(backupDir(path))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Writes the pre-migration on-disk state through a schema-less instance (so the migrator never
     * runs): version 0 plus one user key whose value differs from its key.
     */
    private suspend fun TestScope.seedPreMigration(path: Path) {
        val seed = LevelDBInstance.builder(path)
            .fileSystem(FileSystem.SYSTEM)
            .scope(this)
            .instance { }
            .build()
        try {
            seed.use {
                putValue(versionKey, 0)
                putString("user:keep", "original#val")
            }
            // sanity: the seed actually landed before the migrator touches the dir.
            assertEquals("original#val", seed.use { getString("user:keep") })
        } finally {
            seed.closeAndAwait()
        }
    }

    private fun TestScope.migrationInstance(
        path: Path,
        schema: LevelDBSchema,
    ): LevelDBInstance {
        return LevelDBInstance.builder(path)
            .fileSystem(FileSystem.SYSTEM)
            .scope(this)
            .schema(schema)
            .instance { }
            .build()
    }

    // versionKey for the schema-less seed; mirrors LevelDBSchema's default versionKey.
    private val versionKey: ByteArray
        get() = LevelDBSchema(targetVersion = 0, migrations = emptyList()).versionKey
}

// -------------------- migrations used in these tests --------------------

private class KvMigration(
    override val from: LevelDBSchemaVersion,
    override val to: LevelDBSchemaVersion,
    private val key: String,
    private val value: String,
    private val executed: MutableList<String>,
) : LevelDBMigration {
    override suspend fun migrate(db: LevelDB) {
        executed += key
        db.putString(key, value)
    }
}

/**
 * Records its label, writes a partial marker, then throws a domain error. Used to drive the
 * rollback arms of BACKUP_DIR / STAGING_DB. The pre-throw write is what the rollback must discard.
 */
private class FailingKvMigration(
    override val from: LevelDBSchemaVersion,
    override val to: LevelDBSchemaVersion,
    private val label: String,
    private val executed: MutableList<String>,
) : LevelDBMigration {
    override suspend fun migrate(db: LevelDB) {
        executed += label
        db.putString("preFailMarker", "1")
        throw RuntimeException("boom from $label")
    }
}

// -------------------- fs helpers --------------------

private fun backupDir(dbPath: Path): Path =
    requireNotNull(dbPath.parent) / (dbPath.name + ".leveldb_migration_backup")

private fun stagingDir(dbPath: Path): Path =
    requireNotNull(dbPath.parent) / (dbPath.name + ".leveldb_migration_staging")

private fun deleteTree(path: Path) {
    val fs = FileSystem.SYSTEM
    if (!fs.exists(path)) return

    val metadata = fs.metadata(path)
    if (metadata.isDirectory) {
        fs.list(path).forEach { deleteTree(it) }
    }
    fs.delete(path)
}
