/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

@file:OptIn(ExperimentalStdlibApi::class)

package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.getBytes
import com.edwardstock.leveldb.api.getString
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.config.LevelDBDriverConfig
import com.edwardstock.leveldb.exception.LevelDBConcurrencyException
import com.edwardstock.leveldb.test.MockLevelDB
import com.edwardstock.leveldb.util.stressConfig
import com.edwardstock.leveldb.utils.CoSynchronizedList
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LevelDBInstanceTest {

    private fun TestScope.newInstance(
        path: Path,
        driver: LevelDBDriverConfig = LevelDBDriverConfig(createIfMissing = true),
        scope: CoroutineScope? = null,
        configure: LevelDBInstance.Builder.() -> Unit = {},
    ) = LevelDBInstance.builder(path)
        .scope(scope ?: this)
        .instance {
            driver(driver)
        }
        .apply(configure)
        .build()

    @Test
    fun `parallel and nested contexts reuse single instance`() = runTest {
        val db = newInstance(DatabaseTestCase.createRandomDbPath())

        val job1 = launch {
            db.use { putString("x", "1"); getBytes("x") }
        }
        val job2 = launch {
            db.use {
                putString("y", "2")
                // accidental nested use
                db.use { getBytes("y") }
            }
        }
        job1.join()
        job2.join()

        val v1 = db.use { getString("x") }
        val v2 = db.use { getString("y") }
        assertEquals("1", v1)
        assertEquals("2", v2)
    }

    @Test
    fun `read and write helpers reuse use block`() = runTest {
        val db = newInstance(DatabaseTestCase.createRandomDbPath())
        db.use { putString("a", "1") }
        val v = db.use { getString("a") }
        assertEquals("1", v)
    }

    @Test
    fun `closeAndAwait closes idle DB and allows reopen`() = runTest {
        val db = newInstance(DatabaseTestCase.createRandomDbPath())
        db.use { putString("k", "v") }
        db.closeAndAwait()
        val v = db.use { getString("k") }
        assertEquals("v", v)
    }

    @Test
    fun `close schedules async close without breaking access`() = runTest {
        val db = newInstance(DatabaseTestCase.createRandomDbPath())
        db.use { putString("k", "v") }
        db.close()
        val v = db.use { getString("k") }
        assertEquals("v", v)
    }

    @Test
    fun `check nested calls of different methods`() = runTest {

        LevelDBInstance.builder("path")
            .instance { }


        val file = DatabaseTestCase.createRandomDbPath()
        val db = newInstance(file) {
            instance { }
        }

        assertFailsWith<LevelDBConcurrencyException>("useExclusively must not be called from LevelDBInstance.use()") {
            db.use {
                db.useExclusively { }
            }
        }
        assertFailsWith<LevelDBConcurrencyException>("Nested useExclusively() is forbidden for the same instance") {
            db.useExclusively {
                db.useExclusively {
                    open {
                        putString("k", "v")
                    }
                }
            }
        }
        assertFailsWith<LevelDBConcurrencyException>("use() is forbidden inside useExclusively()") {
            db.useExclusively {
                db.use { }
            }
        }

        // permitted
        db.use {
            db.use { }
        }

        // permitted
        db.useExclusively {
            open { }
        }

        assertFailsWith<LevelDBConcurrencyException>("Nested open() is forbidden in ExclusivePathOps") {
            db.useExclusively {
                open {
                    // makes no sense
                    open { }
                }
            }
        }

        db.use {
            yield()
            db.use { }
        }

        db.use {
            withContext(Dispatchers.Default) {
                db.use { }
            }
        }

        db.use {
            coroutineScope {
                launch {
                    db.use { }
                }
            }
        }
    }

    @Test
    fun `attempt to withPathExclusive after use does not fail`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val db = newInstance(file)

        db.use {}
        db.useExclusively { }
    }

    @Test
    fun `attempt to use inside useExclusively within a different scope fails`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val db = newInstance(file) {
            instance { }
        }


        assertFailsWith<LevelDBConcurrencyException> {
            db.useExclusively {
                it.launch {
                    db.use {}
                }
            }
        }

        assertFailsWith<LevelDBConcurrencyException> {
            db.useExclusively {
                val jobs = mutableListOf<Job>()
                jobs += it.launch {
                    db.use {
                        db.use {}
                    }
                }
                jobs += it.launch {
                    db.use {}
                }
                jobs.joinAll()
            }
        }

        assertFailsWith<TimeoutCancellationException> {
            val scope: TestScope = this
            withTimeout(2.seconds) {
                db.useExclusively {
                    val job = scope.launch {
                        db.use {}
                    }
                    // guaranteed deadlock
                    job.join()
                }
            }
        }
    }

    @Test
    fun `reentrant nested use does not deadlock and does not extend lifetime`() = runTest {
        repeat(10) {
            val file = DatabaseTestCase.createRandomDbPath()
            val db = newInstance(file)

            val enteredExclusive = Channel<Unit>(capacity = 1)
            val leaseEntered = CompletableDeferred<Unit>()
            val outerMayFinish = Channel<Unit>(capacity = 1)

            val outer = launch {
                db.use {
                    leaseEntered.complete(Unit)
                    putString("k", "v0")
                    putString("k", "v1")

                    outerMayFinish.send(Unit) // allow main to release us

                    // nested use should not deadlock
                    db.use { getBytes("k") }
                }
            }

            val exclusive = launch {
                leaseEntered.await()
                // signal we are about to attempt exclusivity (will block on refCount>0)
                db.useExclusively {
                    enteredExclusive.trySend(Unit) // we got exclusivity
                }
            }

            // let outer finish the write (and release the lease)
            outerMayFinish.receive()
            outer.join()
            exclusive.join()

            // After exclusive, DB still usable and value is last write
            val v = db.use { getString("k") }
            assertEquals("v1", v)
            assertTrue(enteredExclusive.tryReceive().isSuccess)
        }
    }

    @Test
    fun `withPathExclusive waits for active leases then runs block`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val db = newInstance(file)

        val stages = CoSynchronizedList<String>()

        val useEntered = CompletableDeferred<Unit>()// exclusive -> main

        // Hold a lease open until we say so
        val holder = launch {
            db.use {
                useEntered.complete(Unit)
                stages += "lease-active"
            }
        }

        // Start exclusive only after the lease is actually acquired
        val exclusive = launch {
            useEntered.await()
            db.useExclusively {
                stages += "exclusive-enter"
            }
            stages += "exclusive-exit"
        }

        holder.join()
        exclusive.join()

        assertEquals(listOf("lease-active", "exclusive-enter", "exclusive-exit"), stages.toList())
    }

    @Test
    fun `exceptions inside write do not poison subsequent ops`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val db = newInstance(file)

        runCatching {
            db.use {
                putString("boom", "1")
                error("mid-failure")
            }
        }
        val v = db.use { getString("boom") }
        assertEquals("1", v)
    }

    @Test
    fun `two instances same path share one native DB`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val a = newInstance(file)
        val b = newInstance(file)

        a.use { putString("z", "9") }
        val fromB = b.use { getString("z") }
        assertEquals("9", fromB)
    }

    /**
     * T14 — one handle per path, by counter.
     *
     * The round-trip test above ("two instances same path share one native DB") passes even if each
     * [LevelDBInstance] opened its OWN native handle: a second handle on the same on-disk LevelDB would
     * still read back a value the first handle wrote (LevelDB persists to disk), so the round-trip
     * cannot distinguish "shared handle" from "two handles racing the same directory".
     *
     * This test pins the actual invariant: two instances built from the SAME [Path] object share ONE
     * [LevelDBEntryState] (via `LevelDBInstance.stateFor` dedup) and that state opens the native DB
     * exactly ONCE (via `ensureOpen` -> `config.createDB`). We observe opens by routing every handle
     * creation through a counting `dbFactory`; the counter is shared by both instances, so it counts
     * total native opens for the path across both.
     *
     * Scope note: both instances here pass the IDENTICAL `file` Path object, so this test does NOT
     * exercise path normalization (`canonicalize`) or the FileSystem-identity key component — a mutant
     * that drops either would still produce equal keys for identical input and survive HERE. Those
     * boundaries are pinned by the dedicated tests below
     * (`two distinct-but-equivalent paths share one native handle`,
     * `same path on different file systems open two separate handles`,
     * `two distinct paths each open their own native handle`). The concurrent open-race guards
     * (`openOnce`, inner double-check, `toClose` discard) are pinned by the concurrent test below; the
     * strict a-then-b ordering here means b returns at the outer fast-path guard and never reaches that
     * machinery.
     *
     * Catches these production mutations:
     *  - `stateFor` minting a fresh [LevelDBEntryState] per instance instead of returning `existing`
     *    from the index: each instance would get its own state, `ensureOpen` would fire `createDB` per
     *    instance -> opens == 2 (and `assertSame` below also fails).
     *  - `ensureOpen` losing its outer `if (entry.db != null) return` guard AND the inner double-check:
     *    b's first `use {}` would call `createDB` again on the already-open shared handle -> opens >= 2.
     * Both surface here as `opens != 1`, while remaining invisible to a pure round-trip assertion.
     */
    @Test
    fun `two instances on one path open the native handle exactly once`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val opens = atomic(0)

        fun TestScope.countingInstance() = newInstance(file) {
            instance {
                dbFactory { p, cfg ->
                    opens.incrementAndGet()
                    NativeLevelDB(p, cfg)
                }
            }
        }

        val a = countingInstance()
        val b = countingInstance()

        // Structural invariant: dedup means the two instances point at the SAME LevelDBEntryState.
        // ensureOpen opens through `entry.db`, so a single shared state is what makes "open once"
        // possible at all. A mutation in stateFor that mints a fresh state per instance (instead of
        // returning `existing` from the index) breaks this directly, independent of the open counter.
        assertSame(
            a.state,
            b.state,
            "two instances on the same canonical path must share one LevelDBEntryState (stateFor dedup)",
        )

        // Keep a's lease OPEN across the whole exercise so the shared handle is never idle-closed
        // between the two opens. With the default Immediate close strategy, a handle is dropped the
        // moment refCount hits 0; overlapping the leases isolates the dedup invariant (one handle per
        // path) from idle-close timing. If a and b did NOT share one LevelDBEntryState, b's open below
        // would call createDB a second time and bump the counter even while a holds the handle open.
        val aLeaseEntered = CompletableDeferred<Unit>()
        val aMayFinish = CompletableDeferred<Unit>()

        val aHolder = launch {
            a.use {
                putString("z", "9")
                aLeaseEntered.complete(Unit)
                aMayFinish.await()
            }
        }

        aLeaseEntered.await()
        // b enters while a still holds the handle: must reuse the same already-open native DB.
        val fromB = b.use { getString("z") }
        assertEquals("9", fromB)

        aMayFinish.complete(Unit)
        aHolder.join()

        assertEquals(
            1,
            opens.value,
            "two instances on the same path must share exactly one native handle while a lease is held " +
                "(stateFor dedup + ensureOpen open-once)",
        )
    }

    /**
     * T14 (concurrent first-open race) — one handle per path under a SIMULTANEOUS first open.
     *
     * The sequential test above strictly orders a-opens-then-b-enters, so b's outer guard at
     * `ensureOpen` (LevelDBInstance.kt:111) already sees `entry.db != null` and returns early — the
     * inner double-check, the `openOnce` mutex, and the `toClose` discard (lines 112-119) are never
     * exercised. Those three exist precisely to win the OPEN RACE: two coroutines both pass the outer
     * `db == null` check, queue on `openOnce`, and only one is allowed to actually call `createDB`.
     *
     * Here both instances' first `use {}` run on a REAL multi-threaded dispatcher
     * ([Dispatchers.Default]); the single-threaded virtual-time test dispatcher cannot exercise this
     * race at all, because `createDB` is synchronous: the first coroutine runs straight through it and
     * installs `entry.db` before ever yielding, so the second always sees the outer guard satisfied.
     *
     * Determinism comes from gating the FACTORY and adding a hard, observable race-entry barrier:
     * the first `createDB` parks (holding `openOnce` in correct production) and does NOT return until
     * the test explicitly releases it. While it is parked, `entry.db` is still null, so the second
     * coroutine — if it genuinely entered the open path — is forced to BLOCK inside `ensureOpen`
     * (on `openOnce`) and cannot complete. The test therefore waits until the second coroutine's job
     * is observably suspended (not completed) WHILE the shared `state.db` is still null, then releases
     * the first open. This is a deterministic proof that the second coroutine reached the open race
     * with the handle uninstalled — it replaces the previous probabilistic wall-clock window, which
     * only proved the FIRST open ran and could leave `opens == 1` vacuously green if the second
     * coroutine never raced.
     *
     * Catches these production mutations that the sequential test cannot:
     *  - Dropping `entry.openOnce.withLock` (ensureOpen serialization): both coroutines pass the outer
     *    check, both call `createDB` -> opens == 2.
     *  - Removing the inner `entry.self.withLock { if (entry.db != null) return }` re-check inside
     *    openOnce: the second coroutine, after the first releases openOnce, would call `createDB` again
     *    -> opens == 2.
     *  - Replacing `if (entry.db == null) entry.db = created else toClose = created` with an
     *    unconditional `entry.db = created` (overwrite-on-race / handle leak): the discarded second
     *    handle would have been a second live open -> opens == 2.
     * All three surface as `opens != 1`. The anti-vacuity barrier below proves the race window was
     * actually entered by the SECOND coroutine (it was suspended inside ensureOpen with db == null)
     * before the first open returned.
     */
    @Test
    fun `concurrent first-open by two instances opens the native handle exactly once`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        // Real multi-threaded scope: use {} runs its open on this scope's dispatcher (Dispatchers.Default),
        // NOT the single-threaded test dispatcher, so the two opens genuinely race on separate threads.
        val realScope = CoroutineScope(Dispatchers.Default)

        val opens = atomic(0)
        val firstFactoryParked = atomic(false)
        // Released by the test ONLY after it has observed the second coroutine suspended inside
        // ensureOpen (job active, shared state.db still null). The first createDB (which holds openOnce
        // in correct production) busy-waits on this flag, so it cannot install the handle until the
        // second coroutine is provably already racing the open.
        val releaseFirstOpen = atomic(false)

        fun countingInstance() = LevelDBInstance.builder(file)
            .scope(realScope)
            .instance {
                driver(LevelDBDriverConfig(createIfMissing = true))
                dbFactory { p, cfg ->
                    val n = opens.incrementAndGet()
                    if (n == 1) {
                        // First open holds openOnce (in correct production). Park (blocking this real
                        // thread) until the test confirms the SECOND coroutine is suspended inside
                        // ensureOpen with the handle still uninstalled, then release.
                        firstFactoryParked.value = true
                        while (!releaseFirstOpen.value) { /* park: hold openOnce + keep db null */ }
                    }
                    NativeLevelDB(p, cfg)
                }
            }
            .build()

        val a = countingInstance()
        val b = countingInstance()
        assertSame(a.state, b.state, "dedup precondition: both instances must share one LevelDBEntryState")

        val aEntered = CompletableDeferred<Unit>()
        val bEntered = CompletableDeferred<Unit>()

        try {
            withContext(Dispatchers.Default) {
                val ja = launch {
                    aEntered.complete(Unit)
                    a.use { putString("z", "9") }
                }
                val jb = launch {
                    bEntered.complete(Unit)
                    b.use { getString("z") }
                }

                aEntered.await()
                bEntered.await()

                // Deterministic race-entry barrier (replaces a probabilistic wall-clock window):
                // wait until (1) the first open is parked (so openOnce is held and state.db is still
                // null), and (2) the second coroutine has reached and SUSPENDED inside the open path
                // (jb still active) while no handle exists. Because state.db is null while the first
                // factory is parked, jb cannot have completed its use{} via the outer fast-path guard;
                // an active-but-not-completing jb under a null db is jb blocked inside ensureOpen on
                // openOnce. We require jb to remain in that state across a stable polling window so it
                // is not merely mid-dispatch before reaching ensureOpen.
                withTimeout(30.seconds) {
                    while (!(firstFactoryParked.value && a.state.db == null)) {
                        yield()
                    }
                    // jb must be racing the open: active (not completed) while db is still uninstalled.
                    var stableActiveWhileNull = 0
                    while (stableActiveWhileNull < 50) {
                        val racing = jb.isActive && a.state.db == null && !jb.isCompleted
                        stableActiveWhileNull = if (racing) stableActiveWhileNull + 1 else 0
                        yield()
                    }
                    assertTrue(
                        jb.isActive && a.state.db == null,
                        "second coroutine did not stay suspended inside ensureOpen with db uninstalled; " +
                            "the concurrent-open race window was not entered",
                    )
                }

                // Release the parked first open; the race now resolves.
                releaseFirstOpen.value = true

                withTimeout(30.seconds) {
                    ja.join()
                    jb.join()
                }
            }

            // Anti-vacuity: the gated first open must actually have been reached (otherwise the race
            // window was never opened and `opens == 1` would be vacuous).
            assertTrue(
                firstFactoryParked.value,
                "the gated first open never ran; the concurrent-open race was not exercised",
            )

            assertEquals(
                1,
                opens.value,
                "concurrent first-open of two instances on the same path must create exactly one " +
                    "native handle (ensureOpen openOnce + inner double-check + toClose discard win the " +
                    "open race)",
            )
        } finally {
            realScope.cancel()
        }
    }

    /**
     * T14 (canonicalization dedup) — two instances built from DISTINCT-but-EQUIVALENT path spellings
     * that resolve to the SAME on-disk directory must share ONE [LevelDBEntryState] and open the
     * native handle exactly ONCE.
     *
     * The sequential/concurrent tests above feed the IDENTICAL [Path] object to both instances, so they
     * cannot tell whether `stateFor` keys on the canonical path or on the raw `path.toString()`: for an
     * identical input both keys are equal. THIS test feeds two textually different paths — a plain
     * `dir/name` and a `dir/name/../name` detour — that `okio` does NOT normalize at construction
     * (`Path.div` keeps `..` segments; `toString()` preserves them), but that `FileSystem.canonicalize`
     * resolves to the same real directory. `stateFor` canonicalizes before keying, so both map to ONE
     * entry; a counting factory shared via the same store proves a single open.
     *
     * Uses the real [FileSystem.SYSTEM]: `canonicalize` only resolves `..` against a real filesystem
     * (for a [FakeFileSystem] it returns the raw `toString()` and would NOT collapse the detour). The
     * factory returns an in-memory [MockLevelDB] keyed by the path string so the two leases can overlap
     * without any native directory lock, isolating the dedup invariant from native open semantics.
     *
     * Catches: `stateFor` dropping `canonicalize(fs, path)` and keying on raw `path.toString()`
     * (named in the headline test's docstring but NOT exercised there). Under that mutation the two
     * spellings produce DIFFERENT keys -> two distinct [LevelDBEntryState] -> [assertSame] fails AND
     * the second instance's `use {}` opens its own handle -> opens == 2.
     */
    @Test
    fun `two distinct-but-equivalent paths share one native handle`() = runTest {
        val dir = DatabaseTestCase.createRandomDbPath()
        // Same real directory, two non-equal spellings. okio does not normalize `..` on div, and
        // toString preserves it, so canonicalPlain != detour as raw strings, but both canonicalize
        // (on the real FS) to the same directory.
        val canonicalPlain = dir
        val detour = dir / ".." / dir.name
        assertNotSame(canonicalPlain, detour)
        assertTrue(
            canonicalPlain.toString() != detour.toString(),
            "the two spellings must differ textually, else this adds nothing over the identical-path test",
        )

        val opens = atomic(0)
        fun TestScope.countingInstance(p: Path) = newInstance(p) {
            instance {
                dbFactory { path, cfg ->
                    opens.incrementAndGet()
                    MockLevelDB(path, cfg)
                }
            }
        }

        val a = countingInstance(canonicalPlain)
        val b = countingInstance(detour)

        assertSame(
            a.state,
            b.state,
            "two distinct-but-equivalent path spellings must canonicalize to one LevelDBEntryState " +
                "(stateFor must canonicalize before keying)",
        )

        // Overlap the leases so the shared handle is never idle-closed between the two opens.
        val aLeaseEntered = CompletableDeferred<Unit>()
        val aMayFinish = CompletableDeferred<Unit>()
        val aHolder = launch {
            a.use {
                putString("z", "9")
                aLeaseEntered.complete(Unit)
                aMayFinish.await()
            }
        }
        aLeaseEntered.await()
        val fromB = b.use { getString("z") }
        assertEquals("9", fromB)
        aMayFinish.complete(Unit)
        aHolder.join()

        assertEquals(
            1,
            opens.value,
            "equivalent paths must share exactly one native handle; opens==2 means stateFor keyed on " +
                "the raw path instead of the canonical path",
        )
    }

    /**
     * T14 (negative dedup) — two GENUINELY DIFFERENT paths must NOT dedup: they get DISTINCT
     * [LevelDBEntryState]s and open TWO separate native handles.
     *
     * Every other T14 case asserts opens == 1, so a mutant that made `stateFor` return a single
     * global/shared state for ALL paths (over-dedup), or made the counter a once-only latch, would
     * survive them all. This case pins the opposite direction: different path -> different state ->
     * opens == 2. Together with the positive cases it proves the open counter tracks real per-path
     * opens rather than being pinned to a constant.
     *
     * Catches: `stateFor` collapsing distinct paths to one entry (e.g. keying on a constant, or
     * ignoring the path component) -> [assertNotSame] fails and opens == 1.
     */
    @Test
    fun `two distinct paths each open their own native handle`() = runTest {
        val opens = atomic(0)
        fun TestScope.countingInstance(p: Path) = newInstance(p) {
            instance {
                dbFactory { path, cfg ->
                    opens.incrementAndGet()
                    MockLevelDB(path, cfg)
                }
            }
        }

        val pathA = DatabaseTestCase.createRandomDbPath()
        val pathB = DatabaseTestCase.createRandomDbPath()
        assertTrue(pathA.toString() != pathB.toString(), "the two paths must be genuinely different")

        val a = countingInstance(pathA)
        val b = countingInstance(pathB)

        assertNotSame(
            a.state,
            b.state,
            "two different paths must NOT share a LevelDBEntryState (stateFor must key on the path)",
        )

        a.use { putString("z", "1") }
        b.use { putString("z", "2") }

        assertEquals(
            2,
            opens.value,
            "two different paths must open two separate native handles; opens==1 means stateFor " +
                "over-deduplicated distinct paths",
        )
    }

    /**
     * T14 (FileSystem-identity dedup) — two instances on the SAME path string but DIFFERENT
     * [FileSystem] objects must NOT share a [LevelDBEntryState]; each opens its own handle.
     *
     * `stateFor` mixes `fsIdentity(fs).hashCode()` into the index key, so the same path under two
     * different filesystems maps to two distinct entries. No other T14 case varies the filesystem
     * (all use the default [FileSystem.SYSTEM]), so the fs-identity key component is constant
     * everywhere else and a mutant dropping it would survive. Here two distinct [FakeFileSystem]
     * instances give the same canonical path string (a [FakeFileSystem] canonicalizes to the raw
     * `toString()`) but different fs identities.
     *
     * Catches: `stateFor` dropping the `fsIdentity(fs).hashCode()` key component (keying on path
     * alone): the two instances would collapse to one entry -> [assertSame] would hold and only one
     * open would happen -> opens == 1. With the component intact they are distinct -> opens == 2.
     */
    @Test
    fun `same path on different file systems open two separate handles`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val opens = atomic(0)

        fun TestScope.countingInstance(fs: FileSystem) = LevelDBInstance.builder(path)
            .scope(this)
            .fileSystem(fs)
            .instance {
                driver(LevelDBDriverConfig(createIfMissing = true))
                dbFactory { p, cfg ->
                    opens.incrementAndGet()
                    MockLevelDB(p, cfg)
                }
            }
            .build()

        val a = countingInstance(FakeFileSystem())
        val b = countingInstance(FakeFileSystem())

        assertNotSame(
            a.state,
            b.state,
            "same path under different FileSystem objects must NOT share a LevelDBEntryState " +
                "(stateFor must mix fsIdentity into the key)",
        )

        a.use { putString("z", "1") }
        b.use { putString("z", "2") }

        assertEquals(
            2,
            opens.value,
            "same path on two different file systems must open two separate handles; opens==1 means " +
                "stateFor dropped the fsIdentity key component",
        )
    }

    /**
     * T14 (idle-close + reopen count) — once every lease drains to refCount 0, the default Immediate
     * close strategy drops the shared handle; a subsequent use must re-open exactly ONCE more, not zero
     * times (stale handle reused after close) and not several times (reopen double-opens).
     *
     * The sequential test deliberately holds a's lease open across b's open to AVOID idle-close, leaving
     * the close-then-reopen edge unpinned. This case pins it. To isolate the FIRST shared open as a
     * single open (so the later increment is unambiguous), a and b overlap their leases exactly as in
     * the held-lease test: opens == 1 while both are inside. Then both drain (Immediate close drops the
     * handle), and a fresh use {} must re-create the handle, taking opens to exactly 2.
     *
     * Catches:
     *  - `closeIfIdle` failing to null out `entry.db` (handle never dropped): the reopen's outer guard
     *    `if (entry.db != null) return` would short-circuit, `createDB` is not called -> opens stays 1.
     *  - a reopen path that double-opens -> opens == 3.
     * Confirms the open counter tracks the handle's real open/close lifecycle, not a once-only latch.
     */
    @Test
    fun `handle reopens exactly once after idle-close drains all leases`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val opens = atomic(0)

        fun TestScope.countingInstance() = newInstance(file) {
            instance {
                dbFactory { p, cfg ->
                    opens.incrementAndGet()
                    NativeLevelDB(p, cfg)
                }
            }
        }

        val a = countingInstance()
        val b = countingInstance()

        // First lifecycle: overlap a and b so the shared handle is open exactly once across both.
        val aLeaseEntered = CompletableDeferred<Unit>()
        val aMayFinish = CompletableDeferred<Unit>()
        val aHolder = launch {
            a.use {
                putString("z", "9")
                aLeaseEntered.complete(Unit)
                aMayFinish.await()
            }
        }
        aLeaseEntered.await()
        val first = b.use { getString("z") }
        assertEquals("9", first)
        assertEquals(1, opens.value, "overlapping first leases must share exactly one open (dedup)")

        // Drain the last lease; Immediate strategy drops the handle. Await any scheduled close.
        aMayFinish.complete(Unit)
        aHolder.join()
        a.closeAndAwait()

        // Reopen on a fresh use {} must create exactly one more native handle.
        val second = a.use { getString("z") }
        assertEquals("9", second)
        assertEquals(
            2,
            opens.value,
            "after idle-close drops the shared handle, the next use must re-open exactly once more (total 2)",
        )
    }

    val ok = atomic(0)

    @Test
    fun `stress many short writes then reads`() {
        ok.value = 0

        runTest {
            val jobs = mutableListOf<Job>()
            val file = DatabaseTestCase.createRandomDbPath()
            val db = newInstance(file)

            val keys = (0 until 300).map { "k$it" }
            coroutineScope {
                keys.chunked(20).forEach { chunk ->
                    jobs += launch {
                        db.use {
                            chunk.forEach { k -> putString(k, k) }
                        }

                    }
                }
            }

            coroutineScope {
                keys.chunked(20).forEach { chunk ->
                    jobs += launch {
                        chunk.forEach { k ->
                            db.use {
                                val v = getString(k)
                                if (k == v) ok.incrementAndGet()
                            }
                        }
                    }
                }
            }

            jobs.joinAll()
            assertEquals(keys.size, ok.value)
        }
    }

    @Test
    fun `stress concurrent use and exclusive access`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val db = newInstance(file)
        val errors = atomic(0)
        val firstError = atomic<Throwable?>(null)
        val exclusiveRequested = atomic(false)
        val activeWorkers = atomic(0)
        val config = stressConfig()

        coroutineScope {
            val workers = (0 until config.workers).map { workerId ->
                launch {
                    repeat(config.iterations) { idx ->
                        while (exclusiveRequested.value) {
                            yield()
                        }
                        runCatching {
                            activeWorkers.incrementAndGet()
                            db.use {
                                if ((idx + workerId) % 2 == 0) {
                                    putString("k$idx", "v$idx")
                                } else {
                                    getBytes("k$idx")
                                }
                            }
                        }.onFailure { err ->
                            firstError.compareAndSet(null, err)
                            errors.incrementAndGet()
                        }.also {
                            activeWorkers.decrementAndGet()
                        }
                    }
                }
            }

            val exclusive = launch {
                repeat(config.exclusiveRepeats) {
                    exclusiveRequested.value = true
                    while (activeWorkers.value > 0) {
                        yield()
                    }
                    db.useExclusively {
                        // Must not call db.use() while exclusive lock is held
                        // Exclusive block is for out-of-band work like compaction or cleanup
                    }
                    exclusiveRequested.value = false
                }
            }

            workers.joinAll()
            exclusive.join()
        }

        firstError.value?.let { throw it }
        assertEquals(0, errors.value)
    }
}
