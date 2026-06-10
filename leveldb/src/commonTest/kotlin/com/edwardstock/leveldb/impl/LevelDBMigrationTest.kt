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
import com.edwardstock.leveldb.currentSchemaVersion
import com.edwardstock.leveldb.exception.LevelDBMigrationException
import com.edwardstock.leveldb.internal.NumberByteArrayConverter
import com.edwardstock.leveldb.migration.LevelDBMigration
import com.edwardstock.leveldb.migration.LevelDBMigrationSafetyPolicy
import com.edwardstock.leveldb.migration.LevelDBSchema
import com.edwardstock.leveldb.migration.LevelDBSchemaVersion
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LevelDBMigrationTest {

    @Test
    fun `migration applies linear chain and leaves metadata clean`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executedSteps = mutableListOf<String>()

        val migrations = listOf(
            RecordingMigration(0, 1, "m0_1", executedSteps),
            RecordingMigration(1, 2, "m1_2", executedSteps),
            RecordingMigration(2, 3, "m2_3", executedSteps),
            // можно хоть дальше, но target=3
        )

        val schema = LevelDBSchema(
            targetVersion = 3,
            migrations = migrations,
        )
        val instance = migrationInstance(path, schema)

        try {
            instance.use { putString("post-migration", "ready") }

            assertEquals(listOf("m0_1", "m1_2", "m2_3"), executedSteps)

            val storedVersion = instance.use {
                val raw = requireNotNull(getBytes(schema.versionKey))
                NumberByteArrayConverter.decode(raw, 4).toInt()
            }
            assertEquals(3, storedVersion)

            assertEquals("ready", instance.use { getString("post-migration") })

            // each RecordingMigration step writes label->label; verify every step's data
            // actually landed and survived in-place migration + reopen (not just in-memory bookkeeping)
            assertEquals("m0_1", instance.use { getString("m0_1") })
            assertEquals("m1_2", instance.use { getString("m1_2") })
            assertEquals("m2_3", instance.use { getString("m2_3") })

            // must be removed after successful migration
            assertNull(instance.use { getBytes(schema.inProgressKey) })
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `migration applies linear chain and leaves metadata clean when migrateIfNeeded called`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executedSteps = mutableListOf<String>()

        val migrations = listOf(
            RecordingMigration(0, 1, "m0_1", executedSteps),
            RecordingMigration(1, 2, "m1_2", executedSteps),
            RecordingMigration(2, 3, "m2_3", executedSteps),
        )

        val schema = LevelDBSchema(
            targetVersion = 3,
            migrations = migrations,
            migrateAutomatically = false
        )
        val instance = migrationInstance(path, schema)

        try {
            // does not fail, but does not apply migration
            instance.use { putString("pre-migration", "ready") }
            assertEquals("ready", instance.use { getString("pre-migration") })
            assertTrue("no migration steps are executed") { executedSteps.isEmpty() }

            // migrate manually
            instance.migrateIfNeeded()

            instance.use { putString("post-migration", "ready") }

            assertEquals(listOf("m0_1", "m1_2", "m2_3"), executedSteps)

            val storedVersion = instance.use {
                val raw = requireNotNull(getBytes(schema.versionKey))
                NumberByteArrayConverter.decode(raw, 4).toInt()
            }
            assertEquals(3, storedVersion)

            assertEquals("ready", instance.use { getString("post-migration") })

            // each RecordingMigration step writes label->label; verify every step's data
            // actually landed and survived the manual migrateIfNeeded + reopen
            assertEquals("m0_1", instance.use { getString("m0_1") })
            assertEquals("m1_2", instance.use { getString("m1_2") })
            assertEquals("m2_3", instance.use { getString("m2_3") })

            // must be removed after successful migration
            assertNull(instance.use { getBytes(schema.inProgressKey) })
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `concurrent use triggers only one migration attempt`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()

        val failedMigrationRan = atomic<Int>(0)
        val schema = LevelDBSchema(
            targetVersion = 2,
            revision = 0,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                RecordingMigration(0, 1, "m0_1", mutableListOf()),
                FailingMigration(1, 2, "m0_2", mutableListOf(), 1000) {
                    failedMigrationRan.value += 1
                },
            )
        )

        val instance = LevelDBInstance.builder(path)
            .fileSystem(FakeFileSystem())
            .scope(this)
            .schema(schema)
            .instance { }
            .build()

        val jobs = mutableListOf<Job>()
        val gate = CompletableDeferred<Unit>()
        repeat(3) {
            jobs += launch {
                gate.await()
                assertFailsWith<LevelDBMigrationException> { instance.use {} }
            }
        }
        gate.complete(Unit)

        jobs.joinAll()
        assertNotNull(instance.state.schemaFailedState)
        assertEquals(1, failedMigrationRan.value)
    }

    @Test
    fun `missing linear step must fail and must not mark schemaOkForVersion as target`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()

        val targetVersion = 2
        val schema = LevelDBSchema(
            targetVersion = targetVersion,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                RecordingMigration(0, 1, "m0_1", mutableListOf()),
                // no migration to v2 — plan 0 -> 2 covers 0 -> 1 then has NO candidate for 1 -> 2,
                // so resolveLinearPlanOrThrow throws "Missing migration 1 -> 2" BEFORE the
                // execution loop (and before any inProgressKey / versionKey write).
            )
        )

        val instance = migrationInstance(path, schema)

        try {
            // The first attempt's exception is the load-bearing one: it must originate from the
            // plan-resolution missing-step branch, NOT from the already-failed corruption guard.
            val firstFailure = withTimeout(3_000) {
                assertFailsWith<LevelDBMigrationException> {
                    instance.use {
                        putString("x", "y")
                    }
                }
            }

            // (a) EXACT exception subtype. assertFailsWith<LevelDBMigrationException> is too broad:
            // LevelDBCorruptedMigrationException EXTENDS LevelDBMigrationException, and the
            // already-failed guard (run() throws LevelDBCorruptedMigrationException when
            // schemaFailedState != null) would also satisfy it. Pinning the EXACT class kills a
            // mutant that routes the missing-step failure through the generic `catch (Throwable)`
            // (which sets schemaFailedState) so that subsequent attempts throw the corrupted
            // subtype: that mutant would make the FIRST attempt still throw the plain type, but the
            // distinction below (schemaFailedState == null) plus the per-retry subtype check seals it.
            assertEquals(
                LevelDBMigrationException::class,
                firstFailure::class,
                "missing-step failure must be the plain plan-resolution error, " +
                        "NOT the LevelDBCorruptedMigrationException already-failed subtype",
            )

            // (b) Exact diagnostic. The gap is 1 -> 2 (m0_1 covers 0 -> 1, then nothing for 1).
            // A mutant that mislabels the failure (e.g. 'Ambiguous'/'Non-linear'/'downgrade') or
            // reports the wrong from/to gap still throws a LevelDBMigrationException and would pass
            // a bare assertFailsWith; pinning the message text kills it. Mirrors the inProgress test.
            assertTrue(
                firstFailure.message?.contains("Missing migration 1 -> 2") == true,
                "Expected the missing-step diagnostic for the 1 -> 2 gap, got: ${firstFailure.message}",
            )

            // (c) The missing-step path is RETRYABLE, not a permanent poison. The plan-resolution
            // throw is caught by `catch (LevelDBMigrationException)` at LevelDBMigrator.kt which
            // deliberately does NOT set schemaFailedState (unlike a step-execution failure). A
            // mutant that routes this through the generic Throwable handler, or that explicitly
            // poisons the schema here, makes schemaFailedState non-null and changes recovery
            // semantics from "re-resolve on next attempt" to "permanently corrupted". assertFailsWith
            // alone cannot see this because the corrupted subtype still satisfies it.
            assertNull(
                instance.state.schemaFailedState,
                "missing-step plan-resolution failure must stay retryable: schemaFailedState must be null",
            )

            // (d) The NAMED contract of this test: the optimistic OK flag must NOT be set to the
            // target. run() must throw before reaching the `schemaOkForVersion = targetVersion`
            // assignment. A mutant that marks schemaOkForVersion = targetVersion on the failure
            // path (or before resolving the plan) is invisible to the on-disk checks below but
            // dies here.
            assertNotEquals(
                targetVersion,
                instance.state.schemaOkForVersion,
                "schemaOkForVersion must NOT be marked as the target when the plan cannot be resolved",
            )

            // Re-run twice more: because the failure is retryable (schemaFailedState stayed null),
            // every attempt must re-resolve the plan and throw the SAME plain missing-step error.
            // A mutant that converts the failure to the corrupted subtype after the first attempt,
            // or that silently no-ops on later attempts, is caught by the per-iteration EXACT-type
            // check here (the loose assertFailsWith<LevelDBMigrationException> would let the
            // corrupted subtype through; assertEquals on the class does not).
            repeat(2) {
                val retry = withTimeout(3_000) {
                    assertFailsWith<LevelDBMigrationException> {
                        instance.use { putString("x", "y") }
                    }
                }
                assertEquals(
                    LevelDBMigrationException::class,
                    retry::class,
                    "each retry must re-resolve and throw the plain missing-step error, " +
                            "not a corrupted-schema subtype",
                )
                assertTrue(
                    retry.message?.contains("Missing migration 1 -> 2") == true,
                    "each retry must report the same 1 -> 2 gap, got: ${retry.message}",
                )
                assertNull(
                    instance.state.schemaFailedState,
                    "retries must not poison the schema into a permanently-failed state",
                )
            }

            // T18: assert the EXACT on-disk state, not `currentSchemaVersion < 2` (which is weak:
            // currentSchemaVersion reads state.db directly and returns 0 once the DB is closed/poisoned,
            // so it would pass even if a mutant durably wrote versionKey=2). The plan for 0 -> 2 is
            // [m0_1] then THROWS "Missing migration 1 -> 2" in resolveLinearPlanOrThrow BEFORE the
            // execution loop, so neither versionKey nor inProgressKey is ever written: both must be
            // absent on disk. Read with a raw handle because the managed instance is poisoned and
            // instance.use{} would re-trigger the throwing migrator.
            val raw = LevelDB.open(path.toString())
            try {
                // No step ran => the schema version key was never persisted at all.
                // A mutant that bumps versionKey before/without a valid plan (or writes the target
                // optimistically) would make this non-null and fail.
                assertNull(
                    raw.getBytes(schema.versionKey),
                    "versionKey must be absent: no step ran, so no version was ever persisted",
                )
                // inProgressKey is only written inside the per-step loop, which the missing-step
                // throw never reaches. A mutant that sets the in-progress marker before resolving
                // the plan would leave this non-null and fail.
                assertNull(
                    raw.getBytes(schema.inProgressKey),
                    "inProgressKey must be absent: the missing-step throw precedes any marker write",
                )
            } finally {
                raw.close()
            }

            withTimeout(3.seconds) {
                assertFails {
                    instance.useExclusively {
                        open {
                            putString("after", "ok")
                        }
                    }
                }
            }
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T3 hardening: the two happy-path tests above read m0_1/m1_2/m2_3 only under the NONE safety
    // policy, and every RecordingMigration writes label->label so a value-corruption or cross-step
    // data-swap mutation is invisible (each key still gets *some* matching label). The tests below
    // close those gaps by (a) reading migrated step data under BACKUP_DIR and STAGING_DB, and
    // (b) writing values that are deliberately != key so a value mangling/swap mutation is caught.
    //
    // The crash-resume / inProgress-validation and cancellation-vs-failure branches are pinned
    // by the seeding-based tests further down (a second LevelDBInstance writes versionKey/
    // inProgressKey on the same path before the migrator runs — the same technique
    // LevelDBMigrationPlanTest uses for its downgrade and stale-marker cases).
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `BACKUP_DIR migration applies linear chain and migrated data is readable after swap`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executedSteps = mutableListOf<String>()

        // Each step writes a value DISTINCT from its key (label -> "$label#val"), so a mutation that
        // writes the wrong value, swaps two steps' data, or mangles values uniformly is detectable —
        // unlike the label->label RecordingMigration used by the NONE happy-path tests.
        val schema = LevelDBSchema(
            targetVersion = 3,
            safety = LevelDBMigrationSafetyPolicy.BACKUP_DIR,
            migrations = listOf(
                KeyValueMigration(0, 1, "m0_1", "m0_1#val", executedSteps),
                KeyValueMigration(1, 2, "m1_2", "m1_2#val", executedSteps),
                KeyValueMigration(2, 3, "m2_3", "m2_3#val", executedSteps),
            ),
        )
        val instance = migrationInstance(path, schema)

        try {
            instance.use { putString("post-migration", "ready") }

            assertEquals(listOf("m0_1", "m1_2", "m2_3"), executedSteps)
            assertEquals(3, readStoredVersion(instance, schema))
            assertEquals("ready", instance.use { getString("post-migration") })

            // Catches a BACKUP_DIR mutation that fails to run migrateInPlace, or restores the
            // pre-migration backup on success: migrated data would be absent / stale.
            // Catches a value-swap/corruption mutation: each key must hold ITS OWN distinct value.
            assertEquals("m0_1#val", instance.use { getString("m0_1") })
            assertEquals("m1_2#val", instance.use { getString("m1_2") })
            assertEquals("m2_3#val", instance.use { getString("m2_3") })

            assertNull(instance.use { getBytes(schema.inProgressKey) })
            // backup dir must be cleaned up on success
            assertTrue(!FileSystem.SYSTEM.exists(backupDir(path)), "backup dir must be removed on success")
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
            deleteTree(backupDir(path))
        }
    }

    @Test
    fun `STAGING_DB migration applies linear chain and migrated data survives the dir swap`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executedSteps = mutableListOf<String>()

        // STAGING_DB migrates a COPY then swaps directories. A mutation that keeps the old (pre-swap)
        // directory, or swaps in the un-migrated copy, would leave these keys absent.
        val schema = LevelDBSchema(
            targetVersion = 3,
            safety = LevelDBMigrationSafetyPolicy.STAGING_DB,
            migrations = listOf(
                KeyValueMigration(0, 1, "m0_1", "m0_1#val", executedSteps),
                KeyValueMigration(1, 2, "m1_2", "m1_2#val", executedSteps),
                KeyValueMigration(2, 3, "m2_3", "m2_3#val", executedSteps),
            ),
        )
        val instance = migrationInstance(path, schema)

        try {
            instance.use { putString("post-migration", "ready") }

            assertEquals(listOf("m0_1", "m1_2", "m2_3"), executedSteps)
            assertEquals(3, readStoredVersion(instance, schema))
            assertEquals("ready", instance.use { getString("post-migration") })

            // The post-swap live DB must contain the migrated values, each distinct, in its own key.
            assertEquals("m0_1#val", instance.use { getString("m0_1") })
            assertEquals("m1_2#val", instance.use { getString("m1_2") })
            assertEquals("m2_3#val", instance.use { getString("m2_3") })

            assertNull(instance.use { getBytes(schema.inProgressKey) })
            // staging + backup scratch dirs must be cleaned up on success
            assertTrue(!FileSystem.SYSTEM.exists(stagingDir(path)), "staging dir must be removed on success")
            assertTrue(!FileSystem.SYSTEM.exists(backupDir(path)), "backup dir must be removed on success")
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
            deleteTree(stagingDir(path))
            deleteTree(backupDir(path))
        }
    }

    @Test
    fun `migration writes distinct values per step including empty key and last-writer-wins`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executedSteps = mutableListOf<String>()

        // Step 0->1: writes an EMPTY-key entry and a normal entry.
        // Step 1->2: overwrites the normal key written by step 0->1 (last-writer-wins across steps).
        // Together these pin: empty key round-trips, per-step values are distinct from keys, and a
        // later step's overwrite of an earlier key is the value that survives. A converter/encoding
        // mutation that mangled values uniformly, or that dropped the second step's write, dies here.
        val schema = LevelDBSchema(
            targetVersion = 2,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                object : LevelDBMigration {
                    override val from = 0
                    override val to = 1
                    override suspend fun migrate(db: LevelDB) {
                        executedSteps += "s0_1"
                        db.putString("", "empty-key-value")
                        db.putString("shared", "from_step_0_1")
                    }
                },
                object : LevelDBMigration {
                    override val from = 1
                    override val to = 2
                    override suspend fun migrate(db: LevelDB) {
                        executedSteps += "s1_2"
                        db.putString("shared", "from_step_1_2")
                    }
                },
            ),
        )
        val instance = migrationInstance(path, schema)

        try {
            instance.use { putString("post-migration", "ready") }

            assertEquals(listOf("s0_1", "s1_2"), executedSteps)
            assertEquals(2, readStoredVersion(instance, schema))

            // Empty-key value must survive migration + reopen.
            assertEquals("empty-key-value", instance.use { getString("") })
            // Last writer wins: step 1->2 overwrote the value step 0->1 put under "shared".
            assertEquals("from_step_1_2", instance.use { getString("shared") })
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `failed step under NONE leaves its partial pre-fail writes in place and marks schema failed`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executedSteps = mutableListOf<String>()

        // Under NONE there is no rollback: FailingMigration writes preFailMarker->1 BEFORE throwing,
        // and migrateInPlace mutates the live DB in place. The first step's data and the failing
        // step's partial write must both be observable after the failure. This pins the documented
        // NONE contract (no recovery) and kills a mutation that either rolls partial writes back
        // under NONE or skips the preFailMarker write.
        val schema = LevelDBSchema(
            targetVersion = 2,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                KeyValueMigration(0, 1, "m0_1", "m0_1#val", executedSteps),
                FailingMigration(1, 2, "m1_2", executedSteps, 0),
            ),
        )
        val instance = migrationInstance(path, schema)

        try {
            assertFailsWith<LevelDBMigrationException> {
                instance.use { putString("x", "y") }
            }

            // First step ran and the failing step started (recorded its label before throwing).
            assertEquals(listOf("m0_1", "m1_2"), executedSteps)
            // schema must be flagged failed so the DB is treated as unusable.
            assertNotNull(instance.state.schemaFailedState)

            // The migrator persists the version only AFTER a step fully succeeds, so the failure at
            // step 1->2 means stored version stops at 1, never reaching the target 2.
            assertTrue(instance.currentSchemaVersion < 2, "version must not reach target after a failed step")

            // Read the in-place DB directly (the managed instance is poisoned). Under NONE the
            // committed step's data AND the failing step's pre-fail partial write both survive.
            val raw = LevelDB.open(path.toString())
            try {
                assertEquals("m0_1#val", raw.getString("m0_1"), "committed step's data must survive under NONE")
                assertEquals("1", raw.getString("preFailMarker"), "failing step's partial write is NOT rolled back under NONE")
            } finally {
                raw.close()
            }
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `invalid on-disk inProgress marker aborts migration instead of silently proceeding`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executed = mutableListOf<String>()

        val schema = LevelDBSchema(
            targetVersion = 3,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                RecordingMigration(0, 1, "m0_1", executed),
                RecordingMigration(1, 2, "m1_2", executed),
                RecordingMigration(2, 3, "m2_3", executed),
            ),
        )

        // Seed a CORRUPT crash-resume marker: storedVersion stays 0 but inProgress=999, which is
        // neither storedVersion+1 (a legitimate retry-current-step marker) nor targetVersion
        // (a legitimate global-target marker). This is the exact state the validation block at
        // LevelDBMigrator.migrateInPlace (the `if (inProgress != null) { ... }` guard) must reject.
        run {
            val seed = LevelDBInstance.builder(path)
                .fileSystem(FileSystem.SYSTEM)
                .scope(this)
                .instance { }
                .build()
            try {
                seed.use {
                    putValue(schema.versionKey, 0)
                    putValue(schema.inProgressKey, 999)
                }
                assertEquals(0, seed.use { getValue<Int>(schema.versionKey) })
                assertEquals(999, seed.use { getValue<Int>(schema.inProgressKey) })
            } finally {
                seed.closeAndAwait()
            }
        }

        val instance = migrationInstance(path, schema)
        try {
            val ex = assertFailsWith<LevelDBMigrationException> {
                instance.use { putString("x", "y") }
            }
            assertTrue(
                ex.message?.contains("inProgress", ignoreCase = true) == true,
                "Expected an invalid-inProgress error, got: ${ex.message}",
            )
            // The validation runs BEFORE the migration loop, so no step may have executed.
            // A mutant that deletes the validation block, or flips `inProgress != expected` to
            // always-false, would let the loop run and `executed` would be non-empty here.
            assertTrue(
                executed.isEmpty(),
                "No migration step may run when the inProgress marker is invalid, ran: $executed",
            )
            // Version must not advance past the corrupt-but-untouched stored value.
            assertTrue(instance.currentSchemaVersion < 3)
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `valid retry-current-step inProgress marker is accepted and migration completes`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executed = mutableListOf<String>()

        val schema = LevelDBSchema(
            targetVersion = 2,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                KeyValueMigration(0, 1, "m0_1", "m0_1#val", executed),
                KeyValueMigration(1, 2, "m1_2", "m1_2#val", executed),
            ),
        )

        // Seed a LEGITIMATE crash-resume marker: a previous run set inProgress = storedVersion + 1
        // (1) right before it crashed without bumping versionKey. The validation block must accept
        // this (inProgress == expected) and the migration must run to completion. This pins the
        // positive arm of the guard: a mutant that flips `inProgress != expected` to always-true
        // (reject every marker) would make this throw instead of completing.
        run {
            val seed = LevelDBInstance.builder(path)
                .fileSystem(FileSystem.SYSTEM)
                .scope(this)
                .instance { }
                .build()
            try {
                seed.use {
                    putValue(schema.versionKey, 0)
                    putValue(schema.inProgressKey, 1)
                }
            } finally {
                seed.closeAndAwait()
            }
        }

        val instance = migrationInstance(path, schema)
        try {
            instance.use { putString("post-migration", "ready") }

            assertEquals(listOf("m0_1", "m1_2"), executed)
            assertEquals(2, readStoredVersion(instance, schema))
            assertEquals("m0_1#val", instance.use { getString("m0_1") })
            assertEquals("m1_2#val", instance.use { getString("m1_2") })
            // marker cleared on success
            assertNull(instance.use { getBytes(schema.inProgressKey) })
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `a cancelled migration step propagates cancellation and does not mark schema permanently failed`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executed = mutableListOf<String>()

        // The failing step throws CancellationException, not a domain error. migrateInPlace has an
        // explicit `catch (CancellationException) { throw e }` BEFORE the generic `catch (Throwable)`
        // that converts step failures into a permanent schemaFailedState. Cancellation must bypass
        // that conversion: it is cooperative shutdown, not a corrupt schema.
        val schema = LevelDBSchema(
            targetVersion = 2,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                KeyValueMigration(0, 1, "m0_1", "m0_1#val", executed),
                CancellingMigration(1, 2, "m1_2", executed),
            ),
        )

        val instance = migrationInstance(path, schema)
        try {
            // The cancellation thrown from inside the step must surface (as a CancellationException
            // or a wrapper carrying it), NOT be swallowed into a normal completion.
            assertFails {
                instance.migrateIfNeeded()
            }

            assertEquals(listOf("m0_1", "m1_2"), executed)
            // The load-bearing assertion: cancellation must NOT be recorded as a permanent
            // schema failure. A mutant that removes the `catch (CancellationException) { throw e }`
            // rethrow lets the generic Throwable handler run and sets schemaFailedState, which
            // would make this non-null.
            assertNull(
                instance.state.schemaFailedState,
                "A cancelled migration step must not be recorded as a permanent schema failure",
            )
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    // Reads the persisted schema version through the managed handle, matching the pattern the
    // NONE happy-path tests use. (instance.currentSchemaVersion reads state.db directly, which is
    // null once the post-migration use{} exits under the Immediate close strategy and would
    // spuriously return 0 — so we must read inside use{} which reopens the DB.)
    private suspend fun readStoredVersion(instance: LevelDBInstance, schema: LevelDBSchema): Int =
        instance.use {
            val raw = requireNotNull(getBytes(schema.versionKey))
            NumberByteArrayConverter.decode(raw, 4).toInt()
        }

    private fun TestScope.migrationInstance(
        path: Path,
        schema: LevelDBSchema,
        fileSystem: FileSystem = FileSystem.SYSTEM,
    ): LevelDBInstance {
        return LevelDBInstance.builder(path)
            .fileSystem(fileSystem)
            .scope(this)
            .schema(schema)
            .instance { }
            .build()
    }

}

// -------------------- migrations used in tests --------------------

private class RecordingMigration(
    override val from: LevelDBSchemaVersion,
    override val to: LevelDBSchemaVersion,
    private val label: String,
    private val executed: MutableList<String>,
) : LevelDBMigration {
    override suspend fun migrate(db: LevelDB) {
        executed += label
        db.putString(label, label)
    }
}

/**
 * Like [RecordingMigration] but writes a value DISTINCT from its key, so cross-step data swaps and
 * value-mangling mutations are detectable (label->label cannot tell those apart).
 */
private class KeyValueMigration(
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
 * Records its label, then throws a [CancellationException] from inside [migrate] to exercise the
 * cancellation-vs-failure distinction in `migrateInPlace` (cancellation must rethrow, not become a
 * permanent schema failure).
 */
private class CancellingMigration(
    override val from: LevelDBSchemaVersion,
    override val to: LevelDBSchemaVersion,
    private val label: String,
    private val executed: MutableList<String>,
) : LevelDBMigration {
    override suspend fun migrate(db: LevelDB) {
        executed += label
        throw CancellationException("cancelled inside $label")
    }
}

private class SlowRecordingMigration(
    override val from: LevelDBSchemaVersion,
    override val to: LevelDBSchemaVersion,
    private val label: String,
    private val executed: MutableList<String>,
    private val delayMs: Long,
) : LevelDBMigration {
    override suspend fun migrate(db: LevelDB) {
        delay(delayMs)
        executed += label
        db.putString(label, label)
    }
}

private class FailingMigration(
    override val from: LevelDBSchemaVersion,
    override val to: LevelDBSchemaVersion,
    private val label: String,
    private val executed: MutableList<String>,
    private val delayMs: Long,
    private val block: () -> Unit = {},
) : LevelDBMigration {
    override suspend fun migrate(db: LevelDB) {
        delay(delayMs)
        executed += label
        db.putString("preFailMarker", "1")
        block()
        throw RuntimeException("boom from $label")
    }
}

// -------------------- fs cleanup --------------------

// Mirror LevelDBMigratorImpl.backupPath / stagingPath using the LevelDBSchema defaults so tests can
// assert the safety-policy scratch directories are cleaned up after a successful migration.
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
