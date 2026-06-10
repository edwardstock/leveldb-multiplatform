/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

@file:OptIn(ExperimentalStdlibApi::class)

package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.getString
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.test.mock
import com.edwardstock.leveldb.util.stressConfig
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T17 — pins the mutual-exclusion contract between [LevelDBInstance.use] (shared access) and
 * [LevelDBInstance.useExclusively] (exclusive barrier) of the concurrency machinery in
 * [com.edwardstock.leveldb.internal.LevelDBEntryState] /
 * [com.edwardstock.leveldb.internal.useExclusivelyInternal].
 *
 * The legacy "stress concurrent use and exclusive access" test ran every worker on the
 * single-threaded virtual-time test dispatcher (no real overlap) and asserted only `errors == 0`.
 * This suite instead:
 *  - runs the stress workers on [Dispatchers.Default] (multiple OS threads) so `use {}` bodies
 *    actually overlap in wall-clock time, and asserts the EXCLUSION INVARIANT rather than the
 *    absence of exceptions; and
 *  - adds *deterministic* (non-probabilistic) tests that pin each individual safety property —
 *    permit-drain, the `exclusiveGate` liveness barrier, exclusive DB-handle access via `open {}`,
 *    cancellation cleanup, and reentrancy — so the relevant production mutations are caught on
 *    every run, not just when a race happens to be hit.
 *
 * The instance is bound to the in-memory MockLevelDB driver so the suite is deterministic and
 * platform-portable; the machinery under test is driver-agnostic.
 *
 * IMPORTANT (why this suite never asserts same-key write serialization):
 * the shared access permit is a 256-permit semaphore — it intentionally lets up to 256 `use {}`
 * bodies run *concurrently*. It serializes shared access against the exclusive barrier ONLY, never
 * shared-vs-shared. The Mock store ([com.edwardstock.leveldb.test.SortedBytesStore]) is an
 * unsynchronized TreeMap, so two `use {}` bodies writing the SAME key on two threads is a data race
 * in the test driver, not a violation of any production guarantee. Asserting "concurrent same-key
 * writes don't corrupt" would therefore test the driver, not production — so this suite gives each
 * worker a disjoint key and never makes that claim.
 */
class LevelDBConcurrencyTest {

    private fun newMockInstance(): LevelDBInstance =
        LevelDBInstance.mock(filePath = "concurrency-${counter.getAndIncrement()}")

    // ---------------------------------------------------------------------------------------------
    // 1. Real-parallelism exclusion invariant (the literal T17 ask).
    // ---------------------------------------------------------------------------------------------

    /**
     * Mutual exclusion under real threads, asserting the invariant (not `errors == 0`).
     *
     * Workers run `use {}` bodies on [Dispatchers.Default]; a competing coroutine runs
     * `useExclusively {}` blocks. Each `use {}` body increments [usersInside] for its whole critical
     * section; each exclusive body sets [exclusiveActive] for its whole critical section. We sample
     * the *other* counter repeatedly across the body (not just twice), so a brief overlap is far more
     * likely to be observed than with a two-point sample.
     *
     * Pinned (probabilistically, by real overlap): if [LevelDBEntryState.acquireSharedPermit] or
     * [LevelDBEntryState.withExclusive]'s `acquireAllPermits()` drain is removed, an exclusive body
     * and a `use {}` body run at the same time and one of the counters becomes non-zero. The
     * deterministic tests below pin the same properties without relying on hitting the race, so this
     * test's role is the genuine multi-thread smoke check the legacy test never performed.
     *
     * Anti-vacuity guards: [maxConcurrentUsers] must reach >= 2 (readers really overlapped) AND
     * [exclusiveContendedWithUser] must be > 0 (an exclusive barrier actually started while at least
     * one reader was live), otherwise the exclusion assertions would be vacuously satisfied by a run
     * that serialized everything.
     */
    @Test
    fun `exclusive blocks never overlap use blocks under real parallelism`() = runTest {
        val db = newMockInstance()
        val config = stressConfig()

        val usersInside = atomic(0)
        val exclusiveActive = atomic(false)

        val useSawExclusive = atomic(0)            // a use{} body observed exclusive active
        val exclusiveSawUsers = atomic(0)          // an exclusive body observed users inside
        val maxConcurrentUsers = atomic(0)
        val exclusiveContendedWithUser = atomic(0) // exclusive started while >=1 reader was queued/active

        val firstError = atomic<Throwable?>(null)

        // Signals when the exclusive coroutine is about to request exclusivity, so the reader
        // workers can keep pressure on while the barrier drains (forces real contention).
        val readersRunning = atomic(0)

        withContext(Dispatchers.Default) {
            coroutineScope {
                val workers = (0 until config.workers).map { workerId ->
                    launch {
                        readersRunning.incrementAndGet()
                        try {
                            repeat(config.iterations) { idx ->
                                runCatching {
                                    db.use {
                                        val now = usersInside.incrementAndGet()
                                        while (true) {
                                            val cur = maxConcurrentUsers.value
                                            if (now <= cur || maxConcurrentUsers.compareAndSet(cur, now)) break
                                        }
                                        // Sample the exclusion invariant repeatedly across the body
                                        // rather than at two fixed instants.
                                        repeat(4) {
                                            if (exclusiveActive.value) useSawExclusive.incrementAndGet()
                                            putString("k$workerId", "$idx")
                                            yield()
                                        }
                                        usersInside.decrementAndGet()
                                    }
                                }.onFailure { firstError.compareAndSet(null, it) }
                            }
                        } finally {
                            readersRunning.decrementAndGet()
                        }
                    }
                }

                val exclusive = launch {
                    repeat(config.exclusiveRepeats * config.workers) {
                        runCatching {
                            // Record that we are racing live readers, so the suite can prove the
                            // barrier actually contended (not merely completed in a quiet gap).
                            if (readersRunning.value > 0 || usersInside.value > 0) {
                                exclusiveContendedWithUser.incrementAndGet()
                            }
                            // Deliberately NOT pre-draining readers: the barrier must win on its own.
                            db.useExclusively {
                                exclusiveActive.value = true
                                repeat(4) {
                                    if (usersInside.value != 0) exclusiveSawUsers.incrementAndGet()
                                    yield()
                                }
                                exclusiveActive.value = false
                            }
                        }.onFailure { firstError.compareAndSet(null, it) }
                        yield()
                    }
                }

                workers.joinAll()
                exclusive.join()
            }
        }

        firstError.value?.let { throw it }

        assertEquals(
            0, useSawExclusive.value,
            "a use {} body executed while an exclusive block was active " +
                "(shared permit / exclusive gate broken)",
        )
        assertEquals(
            0, exclusiveSawUsers.value,
            "an exclusive block executed while use {} bodies were in flight " +
                "(acquireAllPermits drain broken)",
        )
        assertTrue(
            maxConcurrentUsers.value >= 2,
            "expected real parallel use {} overlap but peak concurrency was " +
                "${maxConcurrentUsers.value}; the exclusion assertions would be vacuous",
        )
        assertTrue(
            exclusiveContendedWithUser.value > 0,
            "no exclusive barrier ever started while a reader was live; the drain was never " +
                "stressed and the exclusion assertions would be vacuous",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 2. Deterministic: exclusive barrier drains in-flight readers before its body runs.
    // ---------------------------------------------------------------------------------------------

    /**
     * Pins [LevelDBEntryState.withExclusive] / `useExclusivelyInternal`'s
     * `state.access.acquireAllPermits()` drain, deterministically (no race-hitting required).
     *
     * A reader holds a `use {}` body open (parked on a deferred we control). An exclusive coroutine
     * then requests exclusivity. The exclusive body MUST NOT begin until the reader has left — i.e.
     * `acquireAllPermits()` must wait for the reader's shared permit to be released.
     *
     * FAILS if `acquireAllPermits()` is removed/weakened (e.g. exclusive starts without draining):
     * `exclusiveEntered` would complete while `readerInside` is still true.
     */
    @Test
    fun `exclusive waits for an in-flight reader to drain before entering`() = runTest {
        val db = newMockInstance()

        val readerInside = atomic(false)
        val releaseReader = CompletableDeferred<Unit>()
        val readerEntered = CompletableDeferred<Unit>()
        val readerObservedNoExclusiveOverlap = atomic(true)
        val exclusiveEnteredWhileReaderInside = atomic(false)

        withContext(Dispatchers.Default) {
            coroutineScope {
                val reader = launch {
                    db.use {
                        readerInside.value = true
                        readerEntered.complete(Unit)
                        // Hold the shared permit until explicitly released.
                        releaseReader.await()
                        readerInside.value = false
                    }
                }

                val exclusive = launch {
                    readerEntered.await()
                    db.useExclusively {
                        // If the drain works, the reader has already left here.
                        if (readerInside.value) exclusiveEnteredWhileReaderInside.value = true
                    }
                }

                // Give the exclusive coroutine ample time to (incorrectly) barge in if the drain
                // were broken, then prove it is still parked because the reader holds its permit.
                readerEntered.await()
                repeat(50) { yield() }
                if (readerInside.value && exclusive.isActive) {
                    // expected: exclusive is still blocked behind the live reader
                } else if (!readerInside.value) {
                    // reader somehow left on its own — invalidate, must not happen
                    readerObservedNoExclusiveOverlap.value = false
                }

                releaseReader.complete(Unit)
                reader.join()
                exclusive.join()
            }
        }

        assertTrue(
            readerObservedNoExclusiveOverlap.value,
            "test invariant broke: reader left before being released",
        )
        assertEquals(
            false, exclusiveEnteredWhileReaderInside.value,
            "exclusive block entered while a use {} reader still held its shared permit " +
                "(acquireAllPermits drain broken)",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Deterministic: the exclusiveGate keeps NEW readers out (and out the right way).
    // ---------------------------------------------------------------------------------------------

    /**
     * Pins the `exclusiveGate` liveness barrier in [LevelDBEntryState.acquireSharedPermit].
     *
     * While an exclusive block is held open, a NEW `use {}` is requested. It MUST block until the
     * exclusive block releases — it may not run its body concurrently with exclusivity.
     *
     * Note on what this distinguishes: while the exclusive block holds all 256 permits, a new reader
     * blocks on `access.acquire()` regardless of the gate, so a "does it block at all" check alone
     * cannot isolate the gate from the semaphore. The companion test
     * `exclusive barrier makes progress against a steady stream of readers` isolates the gate's
     * unique role (preventing reader starvation of the drain). Together they pin both halves.
     *
     * FAILS if either the gate or `acquireAllPermits()` lets a `use {}` body run during exclusivity:
     * `lateReaderRanDuringExclusive` becomes true.
     */
    @Test
    fun `a use requested during active exclusivity blocks until release`() = runTest {
        val db = newMockInstance()

        val exclusiveHolding = CompletableDeferred<Unit>()
        val releaseExclusive = CompletableDeferred<Unit>()
        val exclusiveActive = atomic(false)
        val lateReaderEntered = CompletableDeferred<Unit>()
        val lateReaderRanDuringExclusive = atomic(false)

        withContext(Dispatchers.Default) {
            coroutineScope {
                val exclusive = launch {
                    db.useExclusively {
                        exclusiveActive.value = true
                        exclusiveHolding.complete(Unit)
                        releaseExclusive.await()
                        exclusiveActive.value = false
                    }
                }

                // Only request the reader AFTER exclusivity is established.
                exclusiveHolding.await()
                val lateReader = launch {
                    db.use {
                        if (exclusiveActive.value) lateReaderRanDuringExclusive.value = true
                        lateReaderEntered.complete(Unit)
                        putString("late", "v")
                    }
                }

                // The late reader must NOT have entered while exclusivity is active. Give it many
                // scheduler turns to (incorrectly) sneak in, then confirm it is still parked: while
                // exclusivity is held, the reader's body cannot have run, so it has not signalled.
                repeat(50) { yield() }
                assertTrue(
                    !lateReaderEntered.isCompleted,
                    "a use {} requested during active exclusivity entered its body before the " +
                        "exclusive block released (exclusiveGate / acquireAllPermits broken)",
                )

                releaseExclusive.complete(Unit)
                exclusive.join()
                lateReader.join()
            }
        }

        assertEquals(
            false, lateReaderRanDuringExclusive.value,
            "the late use {} body observed exclusiveActive == true — it ran during exclusivity",
        )
    }

    /**
     * Pins the *liveness* role of the `exclusiveGate` in [LevelDBEntryState.acquireSharedPermit]
     * that the semaphore alone does NOT provide.
     *
     * The gate is set (`requestExclusive`) BEFORE `acquireAllPermits()` drains the semaphore. Its job
     * is to stop NEW readers from re-grabbing permits while the drain is in progress; without it, a
     * steady stream of `use {}` calls can keep recycling the 256 permits and starve the exclusive
     * `acquireAllPermits()` indefinitely.
     *
     * Here a pool of readers hammers `use {}` in a tight loop. The exclusive barrier must still make
     * progress and complete. With the gate present, new readers park on `exclusiveGate.await()` once
     * exclusivity is requested, the in-flight permits drain, and the exclusive block runs. If the
     * gate's `await()`/double-check is dropped from `acquireSharedPermit`, the readers can starve the
     * drain and the exclusive block never completes within the timeout.
     *
     * FAILS (times out) if the `exclusiveGate.await()` barrier is removed from `acquireSharedPermit`.
     */
    @Test
    fun `exclusive barrier makes progress against a steady stream of readers`() = runTest {
        val db = newMockInstance()
        val stopReaders = atomic(false)
        val exclusiveDone = atomic(false)
        val firstError = atomic<Throwable?>(null)

        withContext(Dispatchers.Default) {
            coroutineScope {
                // Saturate the path with readers that continuously re-enter use {}.
                val readers = (0 until 16).map { id ->
                    launch {
                        var i = 0
                        while (!stopReaders.value) {
                            runCatching {
                                db.use {
                                    putString("hot$id", "${i++}")
                                    yield()
                                }
                            }.onFailure { firstError.compareAndSet(null, it) }
                            yield()
                        }
                    }
                }

                // The exclusive barrier must complete despite the reader storm.
                val completed = withTimeoutOrNull(10_000) {
                    db.useExclusively {
                        exclusiveDone.value = true
                    }
                    true
                } ?: false

                stopReaders.value = true
                readers.joinAll()

                firstError.value?.let { throw it }
                assertTrue(
                    completed && exclusiveDone.value,
                    "exclusive barrier did not complete while readers kept entering use {} — the " +
                        "exclusiveGate failed to stop new readers and the acquireAllPermits drain " +
                        "was starved",
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 4. Deterministic: exclusive DB-handle access via open {} (the actual I/O path under exclusivity).
    // ---------------------------------------------------------------------------------------------

    /**
     * Exercises the DB-handle path INSIDE the exclusive barrier via [ExclusivePathOps.open], which
     * `useExclusivelyInternal` closes the live handle before entering — `open {}` must reopen and the
     * write must survive after exclusivity ends.
     *
     * A concurrent reader is kept hammering `use {}` while the exclusive block does its `open {}`
     * write, so the exclusive handle work overlaps live shared-access pressure (it does not run in a
     * quiet gap).
     *
     * FAILS if `useExclusivelyInternal`'s close/reopen-under-exclusivity path is broken (e.g. a reader
     * is allowed to observe the nulled/closed handle, or `open {}`'s `ensureOpen` does not respect the
     * barrier): the readers would throw (captured in `firstError`) or the exclusive write would be
     * lost (`assertEquals` below fails).
     */
    @Test
    fun `exclusive open writes survive and never collide with concurrent readers`() = runTest {
        val db = newMockInstance()
        db.use { putString("seed", "0") }

        val stopReaders = atomic(false)
        val firstError = atomic<Throwable?>(null)

        withContext(Dispatchers.Default) {
            coroutineScope {
                val readers = (0 until 8).map { id ->
                    launch {
                        var i = 0
                        while (!stopReaders.value) {
                            runCatching {
                                db.use {
                                    // Reads must always see a valid (non-closed) handle even though
                                    // the exclusive block closes/reopens it.
                                    getString("seed")
                                    putString("r$id", "${i++}")
                                }
                            }.onFailure { firstError.compareAndSet(null, it) }
                            yield()
                        }
                    }
                }

                // Several exclusive sections, each opening the handle and writing under exclusivity.
                repeat(20) { round ->
                    withTimeout(10_000) {
                        db.useExclusively {
                            open { putString("exclusiveKey", "round-$round") }
                        }
                    }
                    yield()
                }

                stopReaders.value = true
                readers.joinAll()
            }
        }

        firstError.value?.let {
            throw AssertionError(
                "a concurrent use {} reader observed a broken DB handle while an exclusive open {} " +
                    "ran (close/reopen-under-exclusivity path broken)",
                it,
            )
        }

        val exclusiveValue = db.use { getString("exclusiveKey") }
        assertEquals(
            "round-19", exclusiveValue,
            "the last exclusive open {} write was lost — exclusive DB-handle write path broken",
        )
    }

    // NOTE on cancellation cleanup (T17 also asked to pin the use {} finally leak-on-cancel path):
    // a deterministic probe (a reader parked inside use {} on Dispatchers.Default, then cancelled)
    // shows that `reader.cancelAndJoin()` NEVER returns — the cancelled coroutine never completes.
    // That is a real production defect: LevelDBInstance.use's finally (lines ~530-554) runs
    // cancellable suspend cleanup (state.self.withLock { refCount-- } / scheduleCloseIfIdle /
    // access.release()) on an already-cancelled coroutine instead of inside withContext(NonCancellable)
    // (the way closeAndAwait does). A correct test for this path can only PASS once production wraps
    // that cleanup in NonCancellable; until then any such test deadlocks. Because this suite uses
    // runTest (virtual time), a withTimeout guard around the join does NOT fire on Dispatchers.Default
    // and the whole module would hang — so the cancellation assertion is reported as a suspected bug
    // (see structured bugNote) rather than shipped as a hanging test.

    // ---------------------------------------------------------------------------------------------
    // 6. Reentrant use {} inside use {}: must reuse the owner's permit/refCount, not double-count.
    // ---------------------------------------------------------------------------------------------

    /**
     * Pins the reentrancy logic in [LevelDBInstance.use] (`ownersDepth` handling, lines ~486-496):
     * a nested `use {}` in the SAME coroutine must reuse the outer owner's refCount (NOT take a
     * second one) and must not deadlock.
     *
     * The observable signature of correct reentrancy is that `refCount` does NOT increase when a
     * nested `use {}` opens inside an outer one for the same Job: the outer takes refCount 1, and the
     * nested reuses it (bumping only `ownersDepth`, not `refCount`). We assert `refCount == 1` both
     * outside and inside the nested body, then `refCount == 0` after the outer exits.
     *
     * We run a SECOND concurrent reader so an exclusive barrier could never sneak in to perturb the
     * count, and we read `refCount` under `state.self` exactly as production mutates it, so the read
     * is consistent.
     *
     * FAILS if reentrant `use {}` takes a fresh refCount/permit instead of reusing the owner's:
     * `refCount` would be 2 inside the nested body. Also FAILS (deadlock -> timeout) if a nested
     * `use {}` blocks on the exclusive gate / a second permit it should not need.
     */
    @Test
    fun `reentrant nested use reuses the owners refCount and does not deadlock`() = runTest {
        val db = newMockInstance()

        suspend fun refCount(): Int = db.state.self.withLock { db.state.refCount }

        withContext(Dispatchers.Default) {
            val nested = withTimeoutOrNull(10_000) {
                db.use {
                    putString("outer", "1")
                    // Outer owner holds exactly one refCount.
                    assertEquals(1, refCount(), "outer use {} did not take exactly one refCount")
                    val v = db.use {
                        // Reentrant: must NOT have added a second refCount (and must not have taken
                        // a second access permit). Production keys reentrancy on
                        // state.ownersDepth[coroutineContext.job], but every use {} wraps its body in
                        // withContext(...) which mints a NEW child Job, so the nested job never equals
                        // the outer's -> the reentrancy branch never fires -> a second refCount/permit
                        // is taken. This assertion pins the intended contract and currently exposes
                        // that defect (see structured bugNote).
                        assertEquals(
                            1, refCount(),
                            "nested use {} took an extra refCount instead of reusing the owner's " +
                                "(reentrancy / ownersDepth broken)",
                        )
                        getString("outer")
                    }
                    v
                }
            } ?: error(
                "reentrant nested use {} deadlocked — it blocked acquiring a resource it should " +
                    "have reused from the outer owner (reentrancy / ownersDepth broken)",
            )

            assertEquals("1", nested, "nested use {} read back the wrong value")

            // refCount must drain back to baseline once the outer owner exits.
            val settled = withTimeoutOrNull(10_000) {
                while (refCount() != 0) yield()
                true
            } ?: false
            assertTrue(
                settled,
                "refCount did not return to 0 after reentrant use {} exited — a nested use {} took " +
                    "an extra refCount that was never released",
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 7. Real-parallel data correctness over DISJOINT keys (never same-key; see class docstring).
    // ---------------------------------------------------------------------------------------------

    /**
     * Data correctness under real parallel writers, each owning a DISJOINT key.
     *
     * This does NOT claim same-key serialization (the shared permit makes no such guarantee, and the
     * Mock TreeMap would race — see class docstring). It pins that genuine multi-thread shared access
     * neither loses a worker's final write to its own key nor cross-contaminates keys, and that an
     * interleaved exclusive barrier (which closes/reopens the handle) does not corrupt committed data.
     *
     * FAILS if shared access drops/cross-writes a worker's own committed value, or a broken exclusive
     * barrier lets `useExclusively` (handle close/reopen) clobber a key's last write.
     */
    @Test
    fun `parallel writers over disjoint keys each observe their own last write`() = runTest {
        val db = newMockInstance()
        val config = stressConfig()
        val workers = config.workers
        val writesPerWorker = config.iterations

        val firstError = atomic<Throwable?>(null)

        withContext(Dispatchers.Default) {
            coroutineScope {
                val writers = (0 until workers).map { workerId ->
                    launch {
                        repeat(writesPerWorker) { idx ->
                            runCatching {
                                db.use { putString("w$workerId", "$idx") }
                            }.onFailure { firstError.compareAndSet(null, it) }
                            if (idx % 7 == 0) yield()
                        }
                        runCatching {
                            db.use { putString("w$workerId", "final-$workerId") }
                        }.onFailure { firstError.compareAndSet(null, it) }
                    }
                }

                // An exclusive barrier interleaved with the writers, closing/reopening the handle.
                val exclusive = launch {
                    repeat(config.exclusiveRepeats) {
                        runCatching {
                            db.useExclusively { open { putString("barrier", "x") } }
                        }.onFailure { firstError.compareAndSet(null, it) }
                        yield()
                    }
                }

                writers.joinAll()
                exclusive.join()
            }
        }

        firstError.value?.let { throw it }

        for (workerId in 0 until workers) {
            val value = db.use { getString("w$workerId") }
            assertEquals(
                "final-$workerId", value,
                "key w$workerId lost its last write under parallel access + interleaved exclusivity",
            )
        }
        // The interleaved exclusive write must also have survived.
        assertEquals("x", db.use { getString("barrier") }, "interleaved exclusive write was lost")
    }

    private companion object {
        // Unique mock path per instance so suites don't share the in-memory store.
        private val counter = atomic(0)
    }
}
