@file:OptIn(ExperimentalCoroutinesApi::class)

package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.log.LevelDBConsoleLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
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
                logger(LevelDBConsoleLogger())
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
