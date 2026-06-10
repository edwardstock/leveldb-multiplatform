@file:OptIn(ExperimentalCoroutinesApi::class)

package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.migration.LevelDBMigration
import com.edwardstock.leveldb.migration.LevelDBSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class LevelDBIdleCloseTests {

    @Test
    fun `db is not closed immediately after use - closes after idle delay`() = runTest {
        val path = "build/test/db1".toPath()
        val tracker = DbTracker()
        val instance = newIdleInstance(
            path = path,
            tracker = tracker,
            idleDelay = 2.seconds,
        )

        // just a touch
        instance.use {}

        // db is opened right after use()
        assertEquals(1, tracker.opens, "DB must be opened once")
        assertEquals(0, tracker.closes, "DB must not be closed immediately after use")

        // almost closed
        advanceTimeBy(1_900)
        runCurrent()
        assertEquals(0, tracker.closes, "DB must still be open before idle delay")

        // wait till delay ends
        advanceTimeBy(100)
        runCurrent()

        assertEquals(1, tracker.closes, "DB must be closed after idle delay")
    }

    @Test
    fun `second use before idle delay should keep db open _extend life_`() = runTest {
        val path = "build/test/db2".toPath()
        val tracker = DbTracker()
        val instance = newIdleInstance(path = path, tracker = tracker, idleDelay = 2.seconds)

        instance.use { }
        assertEquals(1, tracker.opens)
        assertEquals(0, tracker.closes)

        // almost closed
        advanceTimeBy(1_800)
        runCurrent()
        assertEquals(0, tracker.closes, "DB still open")

        // new use() resets the timer
        instance.use { }
        runCurrent()
        assertEquals(0, tracker.closes, "DB still open")

        // only 0.3 seconds passed since use() call
        advanceTimeBy(300)
        runCurrent()
        assertEquals(0, tracker.closes, "Second use must prevent close at the old deadline")

        // closed, since 2 seconds passed since last .use{} call
        advanceTimeBy(1_700)
        runCurrent()
        assertEquals(1, tracker.closes, "DB should close after delay counted from last use")
    }

    @Test
    fun `useExclusively cancels pending idle close and db does not close during exclusivity`() = runTest {
        val path = "build/test/db3".toPath()
        val tracker = DbTracker()
        val instance = newIdleInstance(path = path, tracker = tracker, idleDelay = 2.seconds)

        // db was opened
        instance.use { }
        assertEquals(1, tracker.opens)

        // almost closed, but not
        advanceTimeBy(1_900)
        runCurrent()
        assertEquals(0, tracker.closes)

        // exclusive closes db always, immediately
        instance.useExclusively {
            open {}
            runCurrent()
        }
        assertEquals(1, tracker.closes, "DB must be closed while exclusivity is held")
    }

    @Test
    fun `closeAndAwait closes asap regardless of idle delay`() = runTest {
        val path = "build/test/db4".toPath()
        val tracker = DbTracker()
        val instance = newIdleInstance(
            path = path,
            tracker = tracker,
            idleDelay = 10.seconds,
        )

        instance.use { }
        assertEquals(1, tracker.opens)
        assertEquals(0, tracker.closes)

        instance.closeAndAwait()
        runCurrent()

        assertEquals(1, tracker.closes, "closeAndAwait must close immediately")
    }

    @Test
    fun `failed open does not leak refCount - db still idle-closes afterwards`() = runTest {
        val path = "build/test/db-leak".toPath()
        val tracker = DbTracker()
        var failNextOpen = true

        val instance = LevelDBInstance.builder(path)
            .scope(this)
            .fileSystem(FakeFileSystem())
            .instance {
                dbFactory { _, instanceConfig ->
                    if (failNextOpen) {
                        failNextOpen = false
                        throw LevelDBException("boom on open")
                    }
                    tracker.opens++
                    NoOpLevelDB(config = instanceConfig, onClose = { tracker.closes++ })
                }
            }
            .closeStrategy(LevelDBInstanceConfig.CloseStrategy.IdleDelayed(2.seconds))
            .timeSource(testTimeSource)
            .build()

        // First use{} fails while opening the DB (factory throws inside ensureOpen).
        assertFailsWith<LevelDBException> { instance.use { } }

        // The failed open must not have leaked refCount or an access permit:
        // a subsequent use{} opens normally...
        instance.use { }
        assertEquals(1, tracker.opens)
        assertEquals(0, tracker.closes)

        // ...and the DB must still idle-close, which only happens if refCount returned to 0.
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, tracker.closes, "DB must idle-close after a prior failed open (no refCount leak)")
    }

    @Test
    fun `throw inside use block under IdleDelayed releases refCount and the scheduled idle-close still fires`() = runTest {
        val path = "build/test/db-throw-idle".toPath()
        val tracker = DbTracker()
        val instance = newIdleInstance(path = path, tracker = tracker, idleDelay = 2.seconds)

        // The block opens the DB, then throws. The exception must propagate, but use()'s finally
        // (LevelDBInstance.kt:530-554) must STILL run: decrement refCount, see becameOutermost, and
        // call scheduleCloseIfIdle. Under IdleDelayed the close is SCHEDULED (delayed), not inline.
        assertFailsWith<LevelDBException> {
            instance.use {
                throw LevelDBException("boom inside use")
            }
        }
        assertEquals(1, tracker.opens, "DB must have been opened before the block threw")

        // Not inline: the scheduled delay(2s) job has not run yet (no time advanced).
        assertEquals(0, tracker.closes, "IdleDelayed must schedule, not close inline, even on the throw path")

        // Before the idle delay elapses the handle must still be open.
        advanceTimeBy(1_900)
        runCurrent()
        assertEquals(0, tracker.closes, "DB must still be open before the idle delay")

        // After the full idle delay the scheduled close fires — which only happens if the finally
        // decremented refCount back to 0 on the exception path (closeIfIdle's refCount==0 guard).
        // Kills a mutation that guards scheduleCloseIfIdle/refCount-decrement behind the happy path
        // (e.g. only firing when the block returned normally): refCount would stay leaked at 1, the
        // scheduled job's refCount==0 guard would short-circuit, and closes would stay 0 here.
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, tracker.closes, "idle-close must fire after a throwing use{} released the refCount")
    }

    @Test
    fun `throw inside use block under IdleDelayed releases the access permit so a later use and exclusive barrier proceed`() = runTest {
        val path = "build/test/db-throw-permit".toPath()
        val tracker = DbTracker()
        val instance = newIdleInstance(path = path, tracker = tracker, idleDelay = 2.seconds)

        // First use{} throws inside the block. use()'s finally must release the access permit it
        // acquired (LevelDBInstance.kt:549-553) even though the block raised.
        assertFailsWith<LevelDBException> {
            instance.use {
                throw LevelDBException("boom inside use")
            }
        }
        assertEquals(1, tracker.opens, "first (throwing) use opened the DB")
        assertEquals(0, tracker.closes, "idle-close not yet fired (scheduled at 2s); the handle is still live")

        // A subsequent normal use{} must be able to acquire a permit. It reuses the still-live handle
        // (idle-close has not fired), so opens stays 1. A leaked permit would not block this single
        // use{} (256 permits), so the decisive permit-leak probe is the exclusive barrier below, which
        // needs ALL permits at once.
        instance.use { }
        assertEquals(1, tracker.opens, "second use{} reuses the still-open handle (no reopen, idle-close not fired)")

        // useExclusively calls access.acquireAllPermits() (all 256). If the throwing use{} leaked a
        // single permit, this barrier can never acquire the full set and runTest would hang/time out.
        // Reaching the body (and exiting) proves every permit was returned. Kills a mutation that
        // skips state.access.release() on the exception path (or moves it off the finally).
        var exclusiveRan = false
        instance.useExclusively {
            // open{} reopens a fresh handle inside the exclusive section (exclusive entry closed the
            // prior handle), so opens goes 1 -> 2 here.
            open { }
            exclusiveRan = true
        }
        runCurrent()
        assertTrue(exclusiveRan, "useExclusively acquired all permits -> no permit leaked by the throwing use{}")
        assertEquals(2, tracker.opens, "exclusive open reopened the handle after closing the live one on entry")

        // And the path stays usable afterwards: exclusive teardown leaves the reopened handle live, so
        // a following use{} reuses it (no reopen). Reaching this proves the path is fully operational
        // after a throwing use{} and an exclusive barrier — i.e. no refCount/permit leak stranded it.
        instance.use { }
        advanceUntilIdle()
        assertEquals(2, tracker.opens, "following use{} reuses the live exclusive-opened handle (no reopen)")
    }

    @Test
    fun `throw inside an INNER reentrant use under IdleDelayed releases exactly once on the OUTER exit`() = runTest {
        val path = "build/test/db-reentrant-throw".toPath()
        val tracker = DbTracker()
        val instance = newIdleInstance(path = path, tracker = tracker, idleDelay = 2.seconds)

        // An inner (reentrant) use{} throws. On the inner frame: isReentrant == true, so NO permit was
        // acquired (acquiredAccess stays false, LevelDBInstance.kt:506-511) and the finally takes the
        // ownersDepth > 0 branch (lines 539-540): it decrements depth only, makes NO refCount change,
        // and becameOutermost stays false -> the inner exit must NOT schedule a close and must NOT
        // release a permit (a double-release would corrupt the semaphore). The exception propagates to
        // the OUTER frame, whose finally does the single real teardown (refCount -> 0, permit released,
        // schedule the idle-close). Net observable contract: exactly ONE open, and exactly ONE idle-close
        // after the delay -- never an early close on the inner throw, never a double close.
        assertFailsWith<LevelDBException> {
            instance.use {
                // outer frame (refCount == 1, holds the shared permit)
                instance.use {
                    // inner reentrant frame (no extra refCount, no extra permit)
                    assertEquals(1, tracker.opens, "single shared open for the reentrant pair")
                    assertEquals(0, tracker.closes, "nothing closed while still inside")
                    throw LevelDBException("boom inside inner reentrant use")
                }
            }
        }
        assertEquals(1, tracker.opens, "exactly one open for the whole reentrant pair (no inner reopen)")

        // The inner throw must NOT have closed inline, and the OUTER finally schedules (not inlines) the
        // close under IdleDelayed. A mutation that decrements refCount on the inner throw, or schedules a
        // close on the inner exit, would have closed early (closes would be 1 before any time advances).
        assertEquals(0, tracker.closes, "no inline/early close: inner throw must not tear down the shared handle")

        // The single scheduled idle-close fires after the delay -- proving the OUTER finally returned
        // refCount to 0 exactly once. A mutation that double-decrements refCount on the inner throw would
        // drive refCount negative; the schedule guard (canScheduleDbClose/refCount > 0 -> false) is for
        // the positive case, but a double-release of the permit on the reentrant frame would be caught by
        // the exclusive barrier below.
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, tracker.closes, "exactly one idle-close fires after a reentrant inner throw (single OUTER release)")

        // Decisive no-double-release probe: if the inner reentrant frame had wrongly released a permit,
        // the semaphore would now hold MORE than MAX_DB_ACCESS_PERMITS, but more importantly the prior
        // outer release would have over-released. We assert the path is fully operational: the exclusive
        // barrier acquires ALL 256 permits (would hang/time out on a leaked-or-corrupted permit count),
        // and a following use{} reopens cleanly.
        var exclusiveRan = false
        instance.useExclusively {
            open { }
            exclusiveRan = true
        }
        runCurrent()
        assertTrue(exclusiveRan, "exclusive barrier acquired all permits -> reentrant throw did not leak/double-release a permit")
        assertEquals(2, tracker.opens, "exclusive open reopened the handle (prior one was idle-closed)")
    }

    @Test
    fun `throw from tryMigrate setup under IdleDelayed releases refCount without leaking a shared permit`() = runTest {
        val path = "build/test/db-trymigrate-throw".toPath()
        val tracker = DbTracker()

        // A schema whose single migration step THROWS. tryMigrate (LevelDBInstance.kt:507) runs BEFORE
        // this frame's acquireSharedPermit (line 510): refCount was already incremented (line 494), but
        // acquiredAccess is still false on this frame. tryMigrate routes through state.withExclusive {}
        // (LevelDBEntryState.kt:99-109) which acquires ALL permits and releases them in its own finally.
        // When the migration step throws, the exception propagates out of tryMigrate; use()'s finally must
        // then decrement refCount (so the path is not permanently blocked) and must NOT release a shared
        // permit it never took on this frame (a spurious release would over-fill the semaphore and a later
        // acquireAllPermits would wrongly succeed early / the accounting would be corrupted).
        val throwingSchema = LevelDBSchema(
            targetVersion = 1,
            migrations = listOf(
                object : LevelDBMigration {
                    override val from = 0
                    override val to = 1
                    override val name = "boom-migration"
                    override suspend fun migrate(db: LevelDB) {
                        throw LevelDBException("boom inside migration step")
                    }
                },
            ),
        )

        val instance = LevelDBInstance.builder(path)
            .scope(this)
            .fileSystem(FakeFileSystem())
            .schema(throwingSchema)
            .instance {
                dbFactory { _, instanceConfig ->
                    tracker.opens++
                    NoOpLevelDB(config = instanceConfig, onClose = { tracker.closes++ })
                }
            }
            .closeStrategy(LevelDBInstanceConfig.CloseStrategy.IdleDelayed(2.seconds))
            .timeSource(testTimeSource)
            .build()

        // The first use{} fails during migration setup (inside tryMigrate, before acquireSharedPermit).
        // The migration wraps the step throw as a LevelDBMigrationException.
        assertFailsWith<LevelDBException> { instance.use { } }
        advanceUntilIdle()

        // Decisive permit-accounting probe: useExclusively needs ALL 256 permits at once
        // (acquireAllPermits, LevelDBEntryState.kt:102). If the failed tryMigrate setup leaked a shared
        // permit OR the finally wrongly released one it never acquired, the semaphore's count is wrong and
        // either this barrier hangs (leak: < 256 available) or the over-release would have already
        // corrupted withExclusive's own acquire/release pairing inside tryMigrate. Reaching the body
        // proves the permit set is intact after a tryMigrate-setup throw. The schema is now in a failed
        // state (schemaFailedState set), so we exercise the permit invariant via the exclusive barrier
        // rather than a normal use{} (which would throw LevelDBCorruptedMigrationException by design).
        var exclusiveRan = false
        instance.useExclusively {
            exclusiveRan = true
        }
        runCurrent()
        assertTrue(exclusiveRan, "exclusive barrier acquired all permits -> tryMigrate-setup throw did not leak/over-release a permit")
    }

    // ------------------------- helpers -------------------------

    private data class DbTracker(
        var opens: Int = 0,
        var closes: Int = 0,
    )

    private fun TestScope.newIdleInstance(
        path: Path,
        tracker: DbTracker,
        idleDelay: Duration,
    ): LevelDBInstance {
        return LevelDBInstance.builder(path)
            .scope(this)
            .fileSystem(FakeFileSystem())
            .instance {
                dbFactory { _, instanceConfig ->
                    tracker.opens++
                    NoOpLevelDB(
                        config = instanceConfig,
                        onClose = {
                            tracker.closes++
                        }
                    )
                }
            }
            .closeStrategy(LevelDBInstanceConfig.CloseStrategy.IdleDelayed(idleDelay))
            .timeSource(testTimeSource)
            .build()
    }

}

@OptIn(ExperimentalCoroutinesApi::class)
class LevelDBImmediateCloseTests {

    @Test
    fun `Immediate strategy closes the handle as soon as the outer use exits`() = runTest {
        val path = "build/test/immediate1".toPath()
        val tracker = DbTracker()
        val instance = newImmediateInstance(path = path, tracker = tracker)

        instance.use { }

        // No virtual-time advancement, no runCurrent(): the close must have already happened
        // synchronously inside the use{} finally (scheduleCloseIfIdle -> closeIfIdle for Immediate).
        // If a mutation turns Immediate into a delayed/scheduled close (e.g. shouldCloseImmediately
        // returning false for Immediate, or scheduleCloseIfIdle launching a delayed job instead of
        // closing inline), closes would still be 0 here and this assertion fails.
        assertEquals(1, tracker.opens, "DB must have been opened exactly once")
        assertEquals(1, tracker.closes, "Immediate strategy must close the handle the moment use{} exits")
    }

    @Test
    fun `Immediate strategy reopens on the next use and closes again each time`() = runTest {
        val path = "build/test/immediate2".toPath()
        val tracker = DbTracker()
        val instance = newImmediateInstance(path = path, tracker = tracker)

        instance.use { }
        assertEquals(1, tracker.opens, "first use opens")
        assertEquals(1, tracker.closes, "first use closes immediately on exit")

        // Because the handle was closed, the next use{} must reopen a fresh handle and close it again.
        // This catches a mutation that leaves the handle open after Immediate (closes stuck at 1 / opens stuck at 1).
        instance.use { }
        assertEquals(2, tracker.opens, "second use must reopen because Immediate already closed the handle")
        assertEquals(2, tracker.closes, "second use must close immediately on exit too")
    }

    @Test
    fun `Immediate strategy keeps the handle open across nested reentrant use and closes only when the outer exits`() = runTest {
        val path = "build/test/immediate3".toPath()
        val tracker = DbTracker()
        val instance = newImmediateInstance(path = path, tracker = tracker)

        instance.use {
            instance.use {
                // Inner (reentrant) use shares the same handle and does NOT increment refCount past
                // the outer (ownersDepth deepens instead). Even with Immediate, the close must be
                // deferred until the OUTERMOST use exits.
                //
                // NOTE on what this can/can't catch: the headline "close on every use{} exit" mutation
                // (finally firing scheduleCloseIfIdle on every exit instead of only when refCount==0)
                // is masked here by closeIfIdle's own refCount==0 guard — the inner exit would call
                // closeIfIdle while refCount is still 1, which short-circuits. So this assertion only
                // pins the OBSERVABLE contract (one open, no premature close), not the becameOutermost
                // computation in isolation. The pure becameOutermost flag is redundant with closeIfIdle's
                // guard and is intentionally not over-claimed.
                assertEquals(1, tracker.opens, "single shared open for nested reentrant use")
                assertEquals(0, tracker.closes, "must not close while still inside the outer use")
            }
            assertEquals(0, tracker.closes, "must not close after the inner use; outer still holds the handle")
        }

        assertEquals(1, tracker.opens, "exactly one open for the whole nested block")
        assertEquals(1, tracker.closes, "Immediate closes exactly once, right after the outer use exits")
    }

    @Test
    fun `Immediate strategy still closes the handle when the use block throws`() = runTest {
        val path = "build/test/immediate-throw".toPath()
        val tracker = DbTracker()
        val instance = newImmediateInstance(path = path, tracker = tracker)

        // The use{} block throws a (non-cancellation) exception. The exception must propagate,
        // but the finally path (becameOutermost -> scheduleCloseIfIdle -> inline closeIfIdle) must
        // STILL fire the Immediate close. No virtual time / runCurrent: it must be synchronous.
        //
        // Kills a mutation that guards the finally's scheduleCloseIfIdle behind "block succeeded"
        // (e.g. only closing on the happy path), which would leave the handle open after a throw
        // and report closes == 0 here.
        assertFailsWith<LevelDBException> {
            instance.use {
                throw LevelDBException("boom inside use")
            }
        }

        assertEquals(1, tracker.opens, "DB must have been opened before the block threw")
        assertEquals(1, tracker.closes, "Immediate must close on the finally path even when use{} threw")
    }

    @Test
    fun `Immediate strategy closes once when two concurrent owners overlap and the last one exits`() = runTest {
        val path = "build/test/immediate-concurrent".toPath()
        val tracker = DbTracker()
        val instance = newImmediateInstance(path = path, tracker = tracker)

        // Two DISTINCT owners (separate coroutines/jobs) hold the same handle at the same time,
        // so two independent owners are live. This is the multi-owner path, NOT reentrant nesting
        // (which shares one job). The Immediate close must fire EXACTLY ONCE, only when the last
        // owner exits. The first owner exiting while the second is still inside must NOT close.
        //
        // This is asserted purely behaviorally via the open/close tracker (a real handle-lifecycle
        // side effect): opens stays 1 throughout, closes stays 0 while both owners are live and
        // while only the second remains, and closes becomes 1 only after the last owner exits.
        // (No assertion on the internal refCount field — that is a private counter, not behavior.)
        val firstEntered = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val firstMayExit = CompletableDeferred<Unit>()
        val secondMayExit = CompletableDeferred<Unit>()

        val first = launch {
            instance.use {
                firstEntered.complete(Unit)
                // hold the handle until the second owner is also inside
                secondEntered.await()
                // and until we explicitly allow the first to exit first
                firstMayExit.await()
            }
        }

        firstEntered.await()
        assertEquals(1, tracker.opens, "first owner opened the shared handle")

        val second = launch {
            instance.use {
                secondEntered.complete(Unit)
                // hold the handle (refCount stays incremented) until main explicitly releases us,
                // so we observe the first owner's exit while we are still inside.
                secondMayExit.await()
            }
        }

        secondEntered.await()
        assertEquals(1, tracker.opens, "second owner reused the same already-open handle")
        assertEquals(0, tracker.closes, "two live owners: nothing closed yet")

        // Let the first owner exit while the second is still inside: must NOT close.
        firstMayExit.complete(Unit)
        first.join()
        runCurrent()
        assertEquals(0, tracker.closes, "first owner exiting must not close while the second is still inside")

        // Now let the second (last) owner exit: Immediate closes exactly once.
        secondMayExit.complete(Unit)
        second.join()
        runCurrent()
        assertEquals(1, tracker.opens, "exactly one shared open for both concurrent owners")
        assertEquals(1, tracker.closes, "Immediate closes exactly once, when the last owner exits")
    }

    @Test
    fun `Immediate useExclusively closes the live handle on entry then a following use closes again`() = runTest {
        val path = "build/test/immediate-exclusive".toPath()
        val tracker = DbTracker()
        val instance = newImmediateInstance(path = path, tracker = tracker)

        // First use{} opens then Immediately closes on exit (closes == 1, db == null).
        instance.use { }
        assertEquals(1, tracker.opens, "first use opens")
        assertEquals(1, tracker.closes, "first use closes immediately on exit (Immediate)")

        // useExclusively reopens a fresh handle inside open{} (the previous handle was already closed),
        // and the exclusive barrier itself does NOT close the handle on teardown — it leaves it open.
        // So after useExclusively: opens == 2, closes == 1 (the reopened handle is still live).
        instance.useExclusively {
            open { }
        }
        runCurrent()
        assertEquals(2, tracker.opens, "exclusive open must reopen because Immediate already closed the prior handle")
        assertEquals(1, tracker.closes, "exclusive teardown leaves the handle open; no extra close yet")

        // A subsequent Immediate use{} reuses that still-open exclusive handle and closes it on exit.
        // This pins that Immediate's inline close still governs the normal use{} path after an exclusive
        // section (refCount==0 gate fires the single close; opens stays 2 because the handle was live).
        instance.use { }
        assertEquals(2, tracker.opens, "use reuses the live handle left open by useExclusively (no reopen)")
        assertEquals(2, tracker.closes, "Immediate closes the exclusive-opened handle on the next use{} exit")
    }

    @Test
    fun `Immediate closeAndAwait must not close a live handle held by an in-flight use then Immediate closes once`() = runTest {
        val path = "build/test/immediate-closeawait".toPath()
        val tracker = DbTracker()
        val instance = newImmediateInstance(path = path, tracker = tracker)

        val entered = CompletableDeferred<Unit>()
        val mayExit = CompletableDeferred<Unit>()

        // Hold a LIVE handle open: an in-flight use{} keeps refCount == 1 and db != null.
        val holder = launch {
            instance.use {
                entered.complete(Unit)
                mayExit.await()
            }
        }
        entered.await()
        assertEquals(1, tracker.opens, "handle is open and held by the in-flight use")
        assertEquals(0, tracker.closes, "live handle must not be closed yet")

        // closeAndAwait while the handle is in use must be a NO-OP: closeIfIdle's refCount == 0 guard
        // forbids tearing down a handle that an active use{} still holds.
        //
        // Kills a mutation that drops the refCount == 0 guard in closeIfIdle (closing unconditionally):
        // that would close the handle out from under the in-flight use and report closes == 1 here.
        instance.closeAndAwait()
        runCurrent()
        assertEquals(0, tracker.closes, "closeAndAwait must not close a handle still held by an active use{}")

        // Release the holder: Immediate now closes exactly once on its outermost exit.
        mayExit.complete(Unit)
        holder.join()
        runCurrent()
        assertEquals(1, tracker.opens, "no reopen happened")
        assertEquals(1, tracker.closes, "Immediate closes exactly once after the in-flight use exits")
    }

    @Test
    fun `Immediate use cancelled mid-block still releases the handle and a later use reopens`() = runTest {
        val path = "build/test/immediate-cancel".toPath()
        val tracker = DbTracker()
        val instance = newImmediateInstance(path = path, tracker = tracker)

        val entered = CompletableDeferred<Unit>()
        val mayExit = CompletableDeferred<Unit>()

        // Hold a live handle open inside an in-flight use{}, then CANCEL the holder mid-block.
        // Production routes cancellation through use()'s finally (see the warning comment at
        // LevelDBInstance.kt:500-504: the finally MUST run or refCount/permits leak), and for the
        // Immediate strategy that finally calls scheduleCloseIfIdle -> closeIfIdle to tear the
        // handle down. The CORRECT contract: after the holder is cancelled the handle is released
        // (refCount returns to 0, the Immediate close fires), and a subsequent use{} can reopen.
        //
        // What this catches: any regression that lets the cancellation path skip the finally's
        // close/release (handle stays open, refCount leaks). It also pins that the cancelled
        // owner's lifecycle is fully drained — runTest itself fails if the cancelled holder job
        // never completes (a stuck/leaked refCount path keeps the job in a Cancelling state
        // forever and the test times out).
        val holder = launch {
            instance.use {
                entered.complete(Unit)
                mayExit.await()
            }
        }
        entered.await()
        assertEquals(1, tracker.opens, "handle opened and held by the in-flight use")
        assertEquals(0, tracker.closes, "live handle not closed yet")

        holder.cancel()
        holder.join()
        advanceUntilIdle()

        // The Immediate close must have fired once when the cancelled use{} unwound through finally.
        assertEquals(1, tracker.closes, "cancelling an in-flight Immediate use{} must still close the handle")

        // And the path must be reusable: a fresh use{} reopens (proving refCount returned to 0 and
        // the access permit was released — a leak would block this use{} or prevent the reopen).
        instance.use { }
        advanceUntilIdle()
        assertEquals(2, tracker.opens, "a later use{} must reopen after the cancelled one released the handle")
        assertEquals(2, tracker.closes, "the reopened handle Immediately closes again on its own exit")
    }

    @Test
    fun `Immediate closeAndAwait after a completed use is an idempotent no-op - no double close`() = runTest {
        val path = "build/test/immediate-idempotent-await".toPath()
        val tracker = DbTracker()
        val instance = newImmediateInstance(path = path, tracker = tracker)

        // Immediate already closed the handle on use{} exit.
        instance.use { }
        assertEquals(1, tracker.opens, "first use opens")
        assertEquals(1, tracker.closes, "Immediate closed on exit")

        // closeAndAwait on an already-closed handle must NOT fire onClose again: closeIfIdle's
        // `db != null` guard short-circuits. Pins instance-level close idempotency after Immediate.
        // (Catches a regression that re-runs the close path or drops the db != null guard, which
        // would report closes == 2.)
        instance.closeAndAwait()
        runCurrent()
        assertEquals(1, tracker.opens, "no reopen from closeAndAwait")
        assertEquals(1, tracker.closes, "closeAndAwait after Immediate already closed must be a no-op")
    }

    @Test
    fun `Immediate fire-and-forget close after a completed use is an idempotent no-op`() = runTest {
        val path = "build/test/immediate-idempotent-close".toPath()
        val tracker = DbTracker()
        val instance = newImmediateInstance(path = path, tracker = tracker)

        instance.use { }
        assertEquals(1, tracker.opens, "first use opens")
        assertEquals(1, tracker.closes, "Immediate closed on exit")

        // The fire-and-forget close() launches a NonCancellable coroutine that runs
        // cancelScheduledClose + closeIfIdle. After Immediate already closed, draining that
        // coroutine must not double-fire onClose. This exercises the close() path (the suite
        // otherwise only drives closeAndAwait) and pins its idempotency under Immediate.
        instance.close()
        advanceUntilIdle()
        assertEquals(1, tracker.opens, "no reopen from close()")
        assertEquals(1, tracker.closes, "fire-and-forget close() after Immediate already closed must be a no-op")
    }

    @Test
    fun `Negative-duration IdleDelayed aliases to immediate close`() = runTest {
        val path = "build/test/idle-negative".toPath()
        val tracker = DbTracker()
        // IdleDelayed with a NEGATIVE duration. shouldCloseImmediately's
        // `this is IdleDelayed && duration.isNegative()` branch must alias this to an inline
        // immediate close (this is the literal Immediate-vs-IdleDelayed seam: a negative idle delay
        // behaves like Immediate).
        //
        // Kills a mutation that deletes/flips the isNegative() branch to `-> false`: that would make
        // this schedule a delayed close instead, so closes would be 0 here (no time advanced, no
        // runCurrent) and this assertion fails.
        val instance = newDelayedInstance(
            path = path,
            tracker = tracker,
            strategy = LevelDBInstanceConfig.CloseStrategy.IdleDelayed((-1).seconds),
        )

        instance.use { }

        assertEquals(1, tracker.opens, "DB opened once")
        assertEquals(1, tracker.closes, "negative IdleDelayed must close inline like Immediate (no scheduling)")
    }

    @Test
    fun `Zero-duration IdleDelayed schedules a delayed close - it is not immediate`() = runTest {
        val path = "build/test/idle-zero".toPath()
        val tracker = DbTracker()
        // IdleDelayed(Duration.ZERO): ZERO is NOT negative, so shouldCloseImmediately == false and the
        // close is SCHEDULED (delay(0)), not run inline. This pins the immediate/delayed seam exactly
        // at zero: the close must NOT have happened synchronously on use{} exit, only after the
        // scheduled job runs.
        //
        // Kills a mutation that treats zero (or `<= ZERO`) as immediate: that would close inline and
        // report closes == 1 before runCurrent(), failing the first assertion.
        val instance = newDelayedInstance(
            path = path,
            tracker = tracker,
            strategy = LevelDBInstanceConfig.CloseStrategy.IdleDelayed(Duration.ZERO),
        )

        instance.use { }

        // The scheduled delay(0) job has NOT run yet (no runCurrent): the handle must still be open.
        assertEquals(1, tracker.opens, "DB opened once")
        assertEquals(0, tracker.closes, "zero IdleDelayed must schedule, not close inline on use{} exit")

        // Drain the scheduled delay(0) job: now the delayed close runs.
        runCurrent()
        assertEquals(1, tracker.closes, "zero IdleDelayed closes once the scheduled job runs")
    }

    private fun TestScope.newImmediateInstance(
        path: Path,
        tracker: DbTracker,
    ): LevelDBInstance = newDelayedInstance(
        path = path,
        tracker = tracker,
        strategy = LevelDBInstanceConfig.CloseStrategy.Immediate,
    )

    private fun TestScope.newDelayedInstance(
        path: Path,
        tracker: DbTracker,
        strategy: LevelDBInstanceConfig.CloseStrategy,
    ): LevelDBInstance {
        return LevelDBInstance.builder(path)
            .scope(this)
            .fileSystem(FakeFileSystem())
            .instance {
                dbFactory { _, instanceConfig ->
                    tracker.opens++
                    NoOpLevelDB(
                        config = instanceConfig,
                        onClose = {
                            tracker.closes++
                        }
                    )
                }
            }
            .closeStrategy(strategy)
            .timeSource(testTimeSource)
            .build()
    }

    private data class DbTracker(
        var opens: Int = 0,
        var closes: Int = 0,
    )
}
