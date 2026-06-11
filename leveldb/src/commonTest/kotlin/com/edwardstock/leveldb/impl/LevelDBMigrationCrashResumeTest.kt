/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.LevelDBIterator
import com.edwardstock.leveldb.api.Snapshot
import com.edwardstock.leveldb.api.WriteBatch
import com.edwardstock.leveldb.api.getString
import com.edwardstock.leveldb.api.getValue
import com.edwardstock.leveldb.api.open
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.api.putValue
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.exception.LevelDBCorruptedMigrationException
import com.edwardstock.leveldb.exception.LevelDBDecodingException
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T5 — crash-resume validation + durable-marker pin for
 * [com.edwardstock.leveldb.internal.LevelDBMigratorImpl.migrateInPlace].
 *
 * This suite pins THREE distinct production guarantees of crash-resume:
 *
 * ### 1. The inProgress validation guard (LevelDBMigrator.kt:168-181)
 * ```
 * if (inProgress != null) {
 *     val expected = storedVersion + 1
 *     if (inProgress != expected && inProgress != schema.targetVersion) {
 *         throw LevelDBMigrationException(...)
 *     }
 * }
 * ```
 * Accepts exactly two markers — `storedVersion + 1` (retry-current-step) and `targetVersion`
 * (global target marker) — and rejects everything else as corruption. Two schemas (3-step and
 * 4-step) keep `storedVersion+1`, `storedVersion+2` and `targetVersion` DISTINCT so window-widening,
 * drop-half-of-`&&`, off-by-one, accept-stale and accept-everything mutants are all separated.
 *
 * ### 2. The durable `sync = true` marker writes (LevelDBMigrator.kt:152 / 191 / 211 / 214)
 * Crash-resume only survives a *power loss* (not merely a process crash) because the in-progress
 * marker, the per-step version bump and the final marker clear are all written with `sync = true`.
 * An in-process clean reopen sees byte-identical state whether a write was synced or not, so the
 * on-disk-version / cleared-marker assertions alone CANNOT see a dropped `sync` flag — the previous
 * revision of this suite admitted exactly that gap.
 *
 * We close it by injecting a recording [LevelDBInstanceFactory] (via `instance { dbFactory(...) }`)
 * that wraps the real [NativeLevelDB] and captures `(key, sync)` for every `putBytes` / `del`. The
 * migrator obtains its migration handle through `instance.config.createDB(path)`, i.e. through this
 * factory, so we observe the EXACT `sync` argument it passed for each marker write. A mutant that
 * flips any of those `sync = true` to `false` is then caught directly, in-process — no
 * kill-and-reopen harness required.
 *
 * ### 3. Poisoning vs. retryability — asserted through OBSERVABLE behavior, not the @Volatile field
 * A validation-throw (LevelDBMigrationException, caught at :217) must NOT poison the instance: a
 * retry must re-run and throw the SAME plain error. A decode failure (Throwable, caught at :220)
 * MUST poison it: the next attempt must hit the already-failed guard in `run()` and throw the
 * [LevelDBCorruptedMigrationException] subtype. We assert these consequences, never the private
 * `schemaFailedState` field.
 *
 * ### On-disk version reads use a RAW handle
 * After every run we reopen with [LevelDB.open] and read [LevelDBSchema.versionKey] directly. We do
 * NOT trust the managed `currentSchemaVersion` (it returns 0 once the managed db is closed/poisoned),
 * so a mutant that durably advanced the version before throwing cannot hide.
 *
 * ### Seeding is verified before each run
 * [seedMarkers] reads its writes back and asserts they persisted, so the real assertions can never
 * pass against a never-written marker (no tautology).
 */
class LevelDBMigrationCrashResumeTest {

    // ---------------------------------------------------------------------------------------------
    // ACCEPT arm 1 — inProgress == storedVersion + 1 (retry current step) + durable marker pin
    // ---------------------------------------------------------------------------------------------

    /**
     * stored=1 + inProgress=2 (== storedVersion+1): the legitimate retry-current-step marker.
     * The migrator must ACCEPT it and resume: run 1->2 then 2->3 to completion; 0->1 must NOT re-run.
     *
     * Catches:
     *  - flip `inProgress != expected` to constant `true` (reject every marker): would throw here.
     *  - miscompute `expected` as `storedVersion` (drop the `+1`): marker 2 rejected (1 != 2).
     *  - resume from the wrong version (re-run 0->1): `executed` would start with "m0_1" and
     *    "m0_1#val" would be re-materialised on disk.
     *  - drop `sync = true` on the in-progress marker write (:191), the version bump (:211), or the
     *    final marker clear (:214): the recorded sync flags for the versionKey/inProgressKey writes
     *    would no longer all be `true`.
     */
    @Test
    fun `accept retry marker - stored 1 inProgress 2 resumes 1to2 then 2to3 with durable markers`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executed = mutableListOf<String>()
        val schema = threeStepSchema(executed)
        val recorder = WriteRecorder()

        seedMarkers(path, schema, storedVersion = 1, inProgress = 2)

        val instance = migrationInstance(path, schema, recorder)
        try {
            instance.use { putString("post", "ready") }

            assertEquals(listOf("m1_2", "m2_3"), executed, "must resume at the crashed step, not re-run 0->1")
            assertEquals("m1_2#val", instance.use { getString("m1_2") })
            assertEquals("m2_3#val", instance.use { getString("m2_3") })
            assertNull(instance.use { getString("m0_1") }, "step 0->1 must not re-run when resuming at version 1")
            assertEquals(3, readOnDiskVersion(path, schema), "on-disk version must reach the target")
            assertNull(instance.use { getBytes(schema.inProgressKey) }, "inProgress marker must be cleared on success")

            // --- durable-marker pin (the gap the previous revision could not close) ---
            // Every crash-resume marker write the migrator issued must have carried sync = true:
            //  - inProgressKey writes  -> the in-progress marker (:191) AND its final clear/del (:214)
            //  - versionKey writes     -> the per-step durable version bump (:211)
            assertAllMarkerWritesAreSync(recorder, schema)
            // And it really DID write those markers (anti-vacuous): at least one inProgress write,
            // one inProgress del (the final clear), and one version bump must have been recorded.
            assertTrue(
                recorder.putCountFor(schema.inProgressKey) >= 1,
                "expected at least one in-progress marker write, recorded: ${recorder.describe(schema)}",
            )
            assertTrue(
                recorder.delCountFor(schema.inProgressKey) >= 1,
                "expected the final in-progress marker clear (del), recorded: ${recorder.describe(schema)}",
            )
            assertTrue(
                recorder.putCountFor(schema.versionKey) >= 1,
                "expected at least one durable version bump, recorded: ${recorder.describe(schema)}",
            )

            // A successful resume must not poison the instance: a fresh migrate must be a no-op,
            // NOT throw the already-failed corruption guard. (Observable, not a field read.)
            instance.migrateIfNeeded()
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // ACCEPT arm 2 — inProgress == targetVersion (global target marker), ISOLATED from the retry arm
    // ---------------------------------------------------------------------------------------------

    /**
     * stored=1 + inProgress=4 in a 1..4 schema. targetVersion (4) != storedVersion+1 (2) and
     * != storedVersion+2 (3), so this marker is accepted ONLY through the `|| inProgress == targetVersion`
     * clause and through NO retry-window widening. The migration must complete normally.
     *
     * Catches:
     *  - drop the `inProgress != schema.targetVersion` half of the `&&`: 4 != expected(2) would be
     *    treated as corruption and throw, so this valid resume would fail.
     *  - widen the accept window to `storedVersion+1 .. storedVersion+2` (= {2,3}): 4 would no longer
     *    match (target clause removed) and would throw.
     *  - drop `sync = true` on any marker write (:191/:211/:214): caught by the recorder.
     */
    @Test
    fun `accept target marker - stored 1 inProgress 4 in a 1to4 schema completes with durable markers`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executed = mutableListOf<String>()
        val schema = fourStepSchema(executed)
        val recorder = WriteRecorder()

        seedMarkers(path, schema, storedVersion = 1, inProgress = 4)

        val instance = migrationInstance(path, schema, recorder)
        try {
            instance.use { putString("post", "ready") }

            assertEquals(listOf("m1_2", "m2_3", "m3_4"), executed, "the remaining chain from 1 must run to target 4")
            assertEquals(4, readOnDiskVersion(path, schema), "on-disk version must reach target 4")
            assertNull(instance.use { getBytes(schema.inProgressKey) }, "inProgress marker must be cleared on success")

            assertAllMarkerWritesAreSync(recorder, schema)
            assertTrue(recorder.delCountFor(schema.inProgressKey) >= 1, "expected the final marker clear (del)")
            assertTrue(recorder.putCountFor(schema.versionKey) >= 1, "expected durable version bumps")

            instance.migrateIfNeeded()
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // NOTE on the storedVersion==targetVersion marker-cleanup branch (LevelDBMigrator.kt:150-154):
    // it is UNREACHABLE through the public API and therefore deliberately NOT pinned here. run() has
    // its own `storedVersion == targetVersion` short-circuit at LevelDBMigrator.kt:46 that returns
    // BEFORE migrateInPlace is ever called, so seeding stored==target with a leftover marker exits at
    // run() and never reaches the cleanup del. A test that asserts the marker is cleaned in that case
    // would FAIL against correct production code, so it is intentionally omitted. (See the structured
    // summary for the dead-branch note.)
    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // REJECT — upper adjacent boundary storedVersion+2, ISOLATED from targetVersion
    // ---------------------------------------------------------------------------------------------

    /**
     * stored=1 + inProgress=3 in a 1..4 schema. 3 == storedVersion+2, which is NEITHER the retry
     * marker (expected=2) NOR the target (4). The guard MUST throw and run no step.
     *
     * The 3-step schema masks this (there storedVersion+2 == target == 3). Catches:
     *  - `inProgress in storedVersion+1 .. storedVersion+2` (accept {2,3}): 3 accepted, no throw.
     *  - `inProgress >= expected` / any monotone widening above expected: 3 >= 2 accepted.
     */
    @Test
    fun `reject upper boundary - stored 1 inProgress 3 in a 1to4 schema throws`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executed = mutableListOf<String>()
        val schema = fourStepSchema(executed)

        seedMarkers(path, schema, storedVersion = 1, inProgress = 3)

        assertInvalidMarkerAborts(path, schema, executed, storedVersion = 1)
    }

    // ---------------------------------------------------------------------------------------------
    // REJECT — lower adjacent boundary inProgress == storedVersion (stale already-applied marker)
    // ---------------------------------------------------------------------------------------------

    /**
     * stored=1 + inProgress=1 (== storedVersion). A stale "this step already finished" marker.
     * `1 != expected(2) && 1 != target(3)` => the guard MUST throw.
     *
     * Catches a mutant that broadens the guard to `… && inProgress != storedVersion` (silently accept
     * a stale already-applied marker): inProgress=1 == storedVersion would be accepted and the chain
     * would proceed instead of aborting.
     */
    @Test
    fun `reject lower boundary - stored 1 inProgress 1 stale marker throws`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executed = mutableListOf<String>()
        val schema = threeStepSchema(executed)

        seedMarkers(path, schema, storedVersion = 1, inProgress = 1)

        assertInvalidMarkerAborts(path, schema, executed, storedVersion = 1)
    }

    // ---------------------------------------------------------------------------------------------
    // REJECT — far-away corruption value (the value T5 names)
    // ---------------------------------------------------------------------------------------------

    /**
     * stored=1 + inProgress=6 (== storedVersion+5): the corruption value T5 names. Neither the retry
     * marker (2) nor the target (3). The guard MUST throw and run no step.
     *
     * Catches:
     *  - deleting the whole `if (inProgress != null)` block: the loop would run from version 1.
     *  - flipping `inProgress != expected` to constant `false` (accept everything).
     */
    @Test
    fun `reject far value - stored 1 inProgress 6 throws`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executed = mutableListOf<String>()
        val schema = threeStepSchema(executed)

        seedMarkers(path, schema, storedVersion = 1, inProgress = 6)

        assertInvalidMarkerAborts(path, schema, executed, storedVersion = 1)
    }

    // ---------------------------------------------------------------------------------------------
    // REJECT — sub-stored / negative markers (downgrade-style corruption)
    // ---------------------------------------------------------------------------------------------

    /**
     * stored=2 + inProgress=-1 (negative, below stored). `-1 != expected(3) && -1 != target(3)` => throw.
     * A negative marker is realistic crash corruption.
     *
     * Catches a mutant that accepts anything `<= expected` or `< target` (e.g. `inProgress < target`):
     * -1 < 3 would be accepted and no throw would occur.
     */
    @Test
    fun `reject negative marker - stored 2 inProgress minus 1 throws`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executed = mutableListOf<String>()
        val schema = threeStepSchema(executed)

        seedMarkers(path, schema, storedVersion = 2, inProgress = -1)

        assertInvalidMarkerAborts(path, schema, executed, storedVersion = 2)
    }

    // ---------------------------------------------------------------------------------------------
    // REJECT — wrong-length / corrupt marker bytes (decode failure before the guard) + POISONING
    // ---------------------------------------------------------------------------------------------

    /**
     * inProgressKey holds a 1-byte blob, not a 4-byte Int. `migDb.getValue<Int>(inProgressKey)` at
     * LevelDBMigrator.kt:148 throws [LevelDBDecodingException] ("expected 4 bytes, got 1"), a plain
     * Throwable: it is caught at the outer `catch (e: Throwable)` (:220-229), sets schemaFailedState,
     * and is rethrown. No migration step runs.
     *
     * Unlike the validation-throw arms, a decode failure DOES poison the instance. We pin that via
     * OBSERVABLE behavior: the very next migrate attempt must hit `run()`'s already-failed guard and
     * throw the [LevelDBCorruptedMigrationException] subtype (not the plain decode error again). This
     * proves the instance was poisoned WITHOUT reading the private @Volatile field.
     *
     * Catches a mutant that skips the size check / coerces a short blob to 0 and proceeds, and a
     * mutant that routes the decode failure through the non-poisoning LevelDBMigrationException path.
     */
    @Test
    fun `reject corrupt marker - one byte inProgress blob throws decoding error and poisons instance`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executed = mutableListOf<String>()
        val schema = threeStepSchema(executed)

        // Seed a valid 4-byte version but a deliberately malformed 1-byte inProgress marker.
        val seed = seedHandle(path)
        try {
            seed.use {
                putValue(schema.versionKey, 1)
                putBytes(schema.inProgressKey, byteArrayOf(0x07))
            }
            assertEquals(1, seed.use { getValue<Int>(schema.versionKey) }, "seed versionKey did not persist")
            assertContentEquals(byteArrayOf(0x07), seed.use { getBytes(schema.inProgressKey) }, "corrupt seed marker did not persist")
        } finally {
            seed.closeAndAwait()
        }

        val instance = migrationInstance(path, schema, WriteRecorder())
        try {
            assertFailsWith<LevelDBDecodingException> {
                instance.use { putString("x", "y") }
            }
            assertTrue(executed.isEmpty(), "no migration step may run when the marker bytes are corrupt, ran: $executed")

            // Observable poisoning: the next attempt must be refused by run()'s already-failed guard
            // with the corrupted subtype, not re-run the decode and throw LevelDBDecodingException.
            assertFailsWith<LevelDBCorruptedMigrationException> {
                instance.use { putString("x", "y") }
            }

            // The corrupt run must not have advanced the on-disk version past the seeded 1.
            assertEquals(1, readOnDiskVersion(path, schema), "version must not advance on a corrupt marker")
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // shared assertion for the "invalid marker aborts" arms — behavioral only, no message coupling
    // ---------------------------------------------------------------------------------------------

    /**
     * Runs the schema-bearing migrator against an already-seeded invalid marker and asserts the
     * crash-resume abort CONTRACT (no diagnostic-string coupling):
     *  1. throws [LevelDBMigrationException] (the exact subtype, NOT the corrupted subtype — the
     *     validation throw is caught at :217 and rethrown as-is, it does not poison the schema),
     *  2. NO migration step ran (guard sits BEFORE the per-step loop),
     *  3. the on-disk version (read via a RAW handle) is unchanged at the seeded `storedVersion`,
     *  4. the instance is NOT poisoned — asserted via OBSERVABLE behavior: a retry re-runs the
     *     validation and throws the SAME plain [LevelDBMigrationException] again (a poisoned instance
     *     would instead throw [LevelDBCorruptedMigrationException] from run()'s guard).
     */
    private suspend fun TestScope.assertInvalidMarkerAborts(
        path: Path,
        schema: LevelDBSchema,
        executed: MutableList<String>,
        storedVersion: Int,
    ) {
        val instance = migrationInstance(path, schema, WriteRecorder())
        try {
            val ex = assertFailsWith<LevelDBMigrationException> {
                instance.use { putString("x", "y") }
            }
            // EXACT subtype: LevelDBCorruptedMigrationException extends LevelDBMigrationException, so a
            // bare assertFailsWith would also accept a (wrong) poisoning route. Pin the plain type.
            assertEquals(
                LevelDBMigrationException::class,
                ex::class,
                "the invalid-marker validation throw must be the plain LevelDBMigrationException, " +
                        "not the poisoning LevelDBCorruptedMigrationException subtype",
            )

            assertTrue(executed.isEmpty(), "no migration step may run when the inProgress marker is invalid, ran: $executed")

            // Version must not have advanced past what was seeded (raw read — currentSchemaVersion
            // returns 0 on the now-closed managed db and would hide a durable pre-throw advance).
            assertEquals(
                storedVersion,
                readOnDiskVersion(path, schema),
                "version must stay at the seeded $storedVersion on an invalid marker",
            )

            // Observable non-poisoning: a retry must re-run the validation and throw the SAME plain
            // type. A mutant that poisons the instance on this path would make the retry throw the
            // corrupted subtype instead. (No @Volatile field is read.)
            val retry = assertFailsWith<LevelDBMigrationException> {
                instance.use { putString("x", "y") }
            }
            assertEquals(
                LevelDBMigrationException::class,
                retry::class,
                "an invalid-marker abort must stay retryable: the retry must re-validate and throw " +
                        "the plain type, not a poisoned corrupted-schema subtype",
            )
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    /**
     * Every marker write the migrator recorded (versionKey puts, inProgressKey puts AND dels) must
     * have carried `sync = true`. A dropped `sync` flag at LevelDBMigrator.kt:191/211/214 surfaces
     * here as a recorded `false`.
     */
    private fun assertAllMarkerWritesAreSync(recorder: WriteRecorder, schema: LevelDBSchema) {
        val versionPuts = recorder.putsFor(schema.versionKey)
        val inProgressPuts = recorder.putsFor(schema.inProgressKey)
        val inProgressDels = recorder.delsFor(schema.inProgressKey)

        assertTrue(
            versionPuts.all { it },
            "every durable version bump (:211) must be sync=true, recorded: ${recorder.describe(schema)}",
        )
        assertTrue(
            inProgressPuts.all { it },
            "every in-progress marker write (:191) must be sync=true, recorded: ${recorder.describe(schema)}",
        )
        assertTrue(
            inProgressDels.all { it },
            "every in-progress marker clear (:214) must be sync=true, recorded: ${recorder.describe(schema)}",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    private fun threeStepSchema(executed: MutableList<String>) = LevelDBSchema(
        targetVersion = 3,
        safety = LevelDBMigrationSafetyPolicy.NONE,
        migrations = listOf(
            CrashResumeMigration(0, 1, "m0_1", "m0_1#val", executed),
            CrashResumeMigration(1, 2, "m1_2", "m1_2#val", executed),
            CrashResumeMigration(2, 3, "m2_3", "m2_3#val", executed),
        ),
    )

    private fun fourStepSchema(executed: MutableList<String>) = LevelDBSchema(
        targetVersion = 4,
        safety = LevelDBMigrationSafetyPolicy.NONE,
        migrations = listOf(
            CrashResumeMigration(0, 1, "m0_1", "m0_1#val", executed),
            CrashResumeMigration(1, 2, "m1_2", "m1_2#val", executed),
            CrashResumeMigration(2, 3, "m2_3", "m2_3#val", executed),
            CrashResumeMigration(3, 4, "m3_4", "m3_4#val", executed),
        ),
    )

    private fun TestScope.seedHandle(path: Path): LevelDBInstance =
        LevelDBInstance.builder(path)
            .fileSystem(FileSystem.SYSTEM)
            .scope(this)
            .instance { }
            .build()

    /**
     * Seeds versionKey + inProgressKey through a schema-less instance, then reads them back and
     * asserts they actually persisted before the schema-bearing migrator runs — so the real
     * assertions cannot pass against a never-written marker.
     */
    private suspend fun TestScope.seedMarkers(path: Path, schema: LevelDBSchema, storedVersion: Int, inProgress: Int) {
        val seed = seedHandle(path)
        try {
            seed.use {
                putValue(schema.versionKey, storedVersion)
                putValue(schema.inProgressKey, inProgress)
            }
            assertEquals(storedVersion, seed.use { getValue<Int>(schema.versionKey) }, "seed versionKey did not persist")
            assertEquals(inProgress, seed.use { getValue<Int>(schema.inProgressKey) }, "seed inProgressKey did not persist")
        } finally {
            seed.closeAndAwait()
        }
    }

    /** Reads the on-disk schema version through a RAW [LevelDB] handle (no managed migrator). */
    private fun readOnDiskVersion(path: Path, schema: LevelDBSchema): Int {
        val raw = LevelDB.open(path.toString())
        return try {
            raw.getValue<Int>(schema.versionKey) ?: 0
        } finally {
            raw.close()
        }
    }

    private fun TestScope.migrationInstance(path: Path, schema: LevelDBSchema, recorder: WriteRecorder): LevelDBInstance =
        LevelDBInstance.builder(path)
            .fileSystem(FileSystem.SYSTEM)
            .scope(this)
            .schema(schema)
            .instance {
                // Route EVERY LevelDB handle this instance creates — including the migration handle
                // obtained via `instance.config.createDB(path)` inside migrateInPlace — through a
                // recording wrapper so we can observe the `sync` flag of each marker write.
                dbFactory { p, cfg -> RecordingLevelDB(NativeLevelDB(p, cfg), recorder) }
            }
            .build()
}

// -------------------- write recorder + delegating LevelDB --------------------

/**
 * Records `(key, sync)` for every `putBytes` and `del` issued against any handle it wraps. Keyed by
 * marker so the test can assert the `sync` flag of the migrator's versionKey / inProgressKey writes
 * independently of normal data-plane writes (which target different keys and default to sync=false).
 */
private class WriteRecorder {
    data class Op(val key: ByteArray, val sync: Boolean)

    private val puts = mutableListOf<Op>()
    private val dels = mutableListOf<Op>()

    fun recordPut(key: ByteArray, sync: Boolean) {
        puts += Op(key.copyOf(), sync)
    }

    fun recordDel(key: ByteArray, sync: Boolean) {
        dels += Op(key.copyOf(), sync)
    }

    fun putsFor(key: ByteArray): List<Boolean> = puts.filter { it.key.contentEquals(key) }.map { it.sync }
    fun delsFor(key: ByteArray): List<Boolean> = dels.filter { it.key.contentEquals(key) }.map { it.sync }

    fun putCountFor(key: ByteArray): Int = putsFor(key).size
    fun delCountFor(key: ByteArray): Int = delsFor(key).size

    fun describe(schema: LevelDBSchema): String = buildString {
        append("versionPuts(sync)=").append(putsFor(schema.versionKey))
        append(", inProgressPuts(sync)=").append(putsFor(schema.inProgressKey))
        append(", inProgressDels(sync)=").append(delsFor(schema.inProgressKey))
    }
}

/**
 * Delegates everything to a real [LevelDB] but records the `sync` flag of every write that the
 * migrator could issue against a crash-resume marker. Only `putBytes` and `del` carry a `sync` flag
 * and are the only paths the migrator uses for markers, so those are the only ones recorded.
 */
private class RecordingLevelDB(
    private val delegate: LevelDB,
    private val recorder: WriteRecorder,
) : LevelDB {
    override val config: LevelDBInstanceConfig get() = delegate.config
    override val isClosed: Boolean get() = delegate.isClosed

    override fun close() = delegate.close()

    override fun getBytes(key: ByteArray, snapshot: Snapshot?): ByteArray? = delegate.getBytes(key, snapshot)
    override fun iterator(fillCache: Boolean, snapshot: Snapshot?): LevelDBIterator = delegate.iterator(fillCache, snapshot)
    override fun obtainSnapshot(): Snapshot = delegate.obtainSnapshot()
    override fun getPropertyBytes(key: ByteArray): ByteArray? = delegate.getPropertyBytes(key)

    override fun putBytes(key: ByteArray, value: ByteArray?, sync: Boolean) {
        recorder.recordPut(key, sync)
        delegate.putBytes(key, value, sync)
    }

    override fun write(writeBatch: WriteBatch, sync: Boolean) = delegate.write(writeBatch, sync)

    override fun del(key: ByteArray, sync: Boolean) {
        recorder.recordDel(key, sync)
        delegate.del(key, sync)
    }

    override fun withBatch(batch: WriteBatch?, sync: Boolean, block: WriteBatch.() -> Unit) =
        delegate.withBatch(batch, sync, block)
}

// -------------------- migrations used in tests --------------------

/** Writes a value distinct from its key, so a re-run or data swap is detectable. */
private class CrashResumeMigration(
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

// -------------------- fs cleanup --------------------

private fun deleteTree(path: Path) {
    val fs = FileSystem.SYSTEM
    if (!fs.exists(path)) return

    val metadata = fs.metadata(path)
    if (metadata.isDirectory) {
        fs.list(path).forEach { deleteTree(it) }
    }
    fs.delete(path)
}
