/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.getString
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.exception.LevelDBCorruptedMigrationException
import com.edwardstock.leveldb.exception.LevelDBMigrationException
import com.edwardstock.leveldb.migration.LevelDBMigration
import com.edwardstock.leveldb.migration.LevelDBMigrationSafetyPolicy
import com.edwardstock.leveldb.migration.LevelDBSchema
import com.edwardstock.leveldb.migration.LevelDBSchemaVersion
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * T6 — end-to-end failed-guard contract.
 *
 * The existing `concurrent use triggers only one migration attempt` test in LevelDBMigrationTest
 * sets schemaFailedState but never asserts what happens to *subsequent* use{} / migrateIfNeeded
 * calls after the guard latched. This suite pins that contract:
 *
 *  1. The FIRST use{} whose migration step throws surfaces the plain step-failure
 *     [LevelDBMigrationException] and latches state.schemaFailedState.
 *  2. EVERY later use{} short-circuits in [LevelDBInstance.tryMigrate]
 *     (`if (state.schemaFailedState != null) throw LevelDBCorruptedMigrationException`)
 *     and throws the EXACT corrupted subtype — the migration step does NOT run again.
 *  3. migrateIfNeeded(ignorePreviousFailure = false) hits the same latch inside
 *     LevelDBMigrator.run() and also throws the corrupted subtype without re-running the step.
 *  4. migrateIfNeeded(ignorePreviousFailure = true) clears the latch and re-runs the migrator;
 *     once the migration succeeds, the DB reaches the target version and use{} works again.
 */
class LevelDBMigrationFailedGuardTest {

    @Test
    fun `after a failed migration step the next use throws the corrupted subtype not the plain one`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val attempts = atomic(0)

        // Always fails. targetVersion 1 with a single 0 -> 1 step keeps the plan trivially valid,
        // so the failure is a genuine STEP-EXECUTION failure (sets schemaFailedState), not a
        // plan-resolution error (which would stay retryable and NOT latch the guard).
        val schema = LevelDBSchema(
            targetVersion = 1,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                ToggleMigration(0, 1, "m0_1", failing = { true }, onRun = { attempts.incrementAndGet() }),
            ),
        )

        val instance = migrationInstance(path, schema)
        try {
            // (1) First attempt: the step runs and throws -> plain step-failure exception.
            // assertEquals on the exact class (not assertFailsWith) is load-bearing here: the
            // corrupted subtype EXTENDS LevelDBMigrationException, so a bare assertFailsWith could
            // not tell the first (plain) failure apart from the later (corrupted) ones.
            val first = runCatching { instance.use { putString("x", "y") } }.exceptionOrNull()
            assertNotNull(first, "first migrating use{} must throw")
            assertEquals(
                LevelDBMigrationException::class,
                first::class,
                "first failure must be the PLAIN step-failure type, not the corrupted subtype",
            )
            assertEquals(1, attempts.value, "the failing step must have executed exactly once")

            // The step-failure handler in migrateInPlace must have latched the failure.
            assertNotNull(
                instance.state.schemaFailedState,
                "a failed migration step must latch state.schemaFailedState",
            )

            // (2) Second attempt: tryMigrate must short-circuit on the latched failure and throw the
            // EXACT corrupted subtype WITHOUT re-running the step. A mutant that drops the
            // `if (state.schemaFailedState != null) throw LevelDBCorruptedMigrationException` guard
            // would instead re-enter the migrator (attempts -> 2) and throw the plain type again.
            val second = runCatching { instance.use { putString("x", "y") } }.exceptionOrNull()
            assertNotNull(second, "use{} on a poisoned schema must throw")
            assertEquals(
                LevelDBCorruptedMigrationException::class,
                second::class,
                "after the latch, use{} must throw the corrupted subtype, not the plain migration error",
            )
            assertEquals(
                1, attempts.value,
                "the failed-guard must short-circuit: the step must NOT run again on a poisoned schema",
            )

            // Third attempt: still latched, still the corrupted subtype, still no re-run.
            val third = runCatching { instance.use { putString("x", "y") } }.exceptionOrNull()
            assertEquals(LevelDBCorruptedMigrationException::class, third?.let { it::class })
            assertEquals(1, attempts.value, "repeated use{} must keep short-circuiting")
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `migrateIfNeeded without ignore flag throws corrupted subtype and does not re-run the step`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val attempts = atomic(0)

        val schema = LevelDBSchema(
            targetVersion = 1,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                ToggleMigration(0, 1, "m0_1", failing = { true }, onRun = { attempts.incrementAndGet() }),
            ),
        )

        val instance = migrationInstance(path, schema)
        try {
            // Latch the failure via an automatic-migration use{}.
            runCatching { instance.use { putString("x", "y") } }
            assertNotNull(instance.state.schemaFailedState)
            assertEquals(1, attempts.value)

            // migrateIfNeeded(ignorePreviousFailure = false): does NOT clear the latch, so
            // LevelDBMigrator.run() hits `if (state.schemaFailedState != null) throw
            // LevelDBCorruptedMigrationException` before touching the step. A mutant that clears the
            // latch unconditionally (ignoring the flag) would re-run the step (attempts -> 2).
            val ex = runCatching { instance.migrateIfNeeded(ignorePreviousFailure = false) }.exceptionOrNull()
            assertNotNull(ex, "migrateIfNeeded on a poisoned schema must throw")
            assertEquals(
                LevelDBCorruptedMigrationException::class,
                ex::class,
                "migrateIfNeeded(ignorePreviousFailure=false) must throw the corrupted subtype",
            )
            assertEquals(
                1, attempts.value,
                "ignorePreviousFailure=false must NOT clear the latch, so the step must not re-run",
            )
            assertNotNull(
                instance.state.schemaFailedState,
                "the latch must remain set after a non-ignoring migrateIfNeeded",
            )
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `migrateIfNeeded with ignore flag resets the latch and re-runs to a usable DB`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val attempts = atomic(0)
        // The step fails on its first execution and succeeds on every subsequent one, modelling a
        // transient failure the caller remediates before retrying with ignorePreviousFailure=true.
        val shouldFail = atomic(true)

        val schema = LevelDBSchema(
            targetVersion = 1,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                ToggleMigration(
                    0, 1, "m0_1",
                    failing = { shouldFail.value },
                    onRun = { attempts.incrementAndGet() },
                    onSuccess = { db -> db.putString("migrated", "done") },
                ),
            ),
        )

        val instance = migrationInstance(path, schema)
        try {
            // Latch the failure.
            val first = runCatching { instance.use { putString("x", "y") } }.exceptionOrNull()
            assertEquals(LevelDBMigrationException::class, first?.let { it::class })
            assertNotNull(instance.state.schemaFailedState)
            assertEquals(1, attempts.value)

            // Remediate, then recover: ignorePreviousFailure=true clears state.schemaFailedState and
            // re-runs migrator.run(). The step now succeeds, so the migration completes.
            shouldFail.value = false
            instance.migrateIfNeeded(ignorePreviousFailure = true)

            // The latch must be cleared by a successful recovery run.
            assertNull(
                instance.state.schemaFailedState,
                "a successful recovery via ignorePreviousFailure=true must clear the latch",
            )
            // The step ran a SECOND time (the recovery run). A mutant that throws away the ignore
            // flag, or that does not re-run the migrator after clearing the latch, would leave
            // attempts at 1 (and either rethrow or leave the schema un-migrated).
            assertEquals(2, attempts.value, "the recovery run must re-execute the migration step")

            // End-to-end proof the DB is usable again: the step's write is visible and ordinary
            // use{} no longer throws. If the guard wrongly stayed latched, this use{} would throw
            // the corrupted subtype instead of returning the value.
            assertEquals("done", instance.use { getString("migrated") })

            // The persisted schema version reached the target, so a later use{} performs no further
            // migration (attempts stays at 2). This pins that recovery durably advanced the version.
            assertEquals("done", instance.use { getString("migrated") })
            assertEquals(2, attempts.value, "version reached target: no further migration on later use{}")
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    private fun TestScope.migrationInstance(
        path: Path,
        schema: LevelDBSchema,
    ): LevelDBInstance =
        LevelDBInstance.builder(path)
            .fileSystem(FileSystem.SYSTEM)
            .scope(this)
            .schema(schema)
            .instance { }
            .build()
}

/**
 * Single migration step whose failure is controllable per-invocation via [failing]. [onRun] fires on
 * every execution (to count attempts) and [onSuccess] runs only on a non-failing pass (to leave
 * observable migrated data). Throwing a plain [RuntimeException] routes through migrateInPlace's
 * step-failure handler, which latches state.schemaFailedState — exactly the path T6 guards.
 */
private class ToggleMigration(
    override val from: LevelDBSchemaVersion,
    override val to: LevelDBSchemaVersion,
    override val name: String,
    private val failing: () -> Boolean,
    private val onRun: () -> Unit = {},
    private val onSuccess: suspend (LevelDB) -> Unit = {},
) : LevelDBMigration {
    override suspend fun migrate(db: LevelDB) {
        onRun()
        if (failing()) {
            throw RuntimeException("boom from $name")
        }
        onSuccess(db)
    }
}

private fun deleteTree(path: Path) {
    val fs = FileSystem.SYSTEM
    if (!fs.exists(path)) return
    val metadata = fs.metadata(path)
    if (metadata.isDirectory) {
        fs.list(path).forEach { deleteTree(it) }
    }
    fs.delete(path)
}
