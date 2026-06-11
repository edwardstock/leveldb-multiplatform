package com.edwardstock.leveldb.internal

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import okio.Path
import okio.Path.Companion.toPath
import kotlin.concurrent.Volatile
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal data class LevelDBEntryState(
    val path: String,
    val fsId: Any,
    val self: Mutex = Mutex(),           // guards db/refCount/ownersDepth
    val openOnce: Mutex = Mutex(),        // serialize open
    @Volatile var schemaOkForVersion: Int? = null,  // cheap cache for migration checks
    @Volatile var schemaFailedState: LevelDBSchemaFailedState? = null,
    var db: LevelDB? = null,
    // Keyed on the stable ReentryToken (carried in the coroutine context), NOT on
    // coroutineContext.job: use() wraps its body in withContext(...), which mints a fresh
    // child Job every call — including reentrant ones — so a Job key never matches across
    // nesting and reentrancy detection silently fails (double refCount + 2nd permit -> deadlock).
    val ownersDepth: MutableMap<ReentryToken, Int> = mutableMapOf(),
    var refCount: Int = 0,
    val access: Semaphore = Semaphore(MAX_DB_ACCESS_PERMITS),
    val exclusiveMutex: Mutex = Mutex(),
    @Volatile var exclusiveGate: CompletableDeferred<Unit>? = null,
    var idleCloseJob: Job? = null,
    var lastAccessMark: TimeMark? = null,
) {
    val pathOkio: Path get() = path.toPath()

    // use under [self] mutex only
    internal suspend fun markUsing(timeSource: TimeSource) {
        self.withLock {
            idleCloseJob?.cancel()
            idleCloseJob = null
            lastAccessMark = timeSource.markNow()
        }
    }

    internal suspend fun canScheduleDbClose(): Boolean = self.withLock {
        return when {
            // already scheduled
            idleCloseJob != null -> false
            // somebody uses the instance
            refCount > 0 -> false
            // db is closed, no need to schedule close
            db == null -> false
            else -> true
        }
    }

    internal suspend fun canCloseDb(closeStrategy: LevelDBInstanceConfig.CloseStrategy): Boolean = self.withLock {
        when {
            // cannot close if somebody uses the instance
            refCount != 0 -> false
            // cannot close if db is not opened
            db == null -> false
            // close if last usage mark was not set
            lastAccessMark == null -> true
            else -> {
                val mark = lastAccessMark!!
                mark.elapsedNow() >= closeStrategy.duration
            }
        }
    }

    internal suspend fun acquireSharedPermit() {
        while (true) {
            // wait exclusive gate whilte it's in progress
            exclusiveGate?.await()

            // take access permit
            access.acquire()

            // double-check: exclusive might be started between await() and acquire()
            val gateAfter = exclusiveGate
            if (gateAfter == null || gateAfter.isCompleted) return

            // we are in exclusive mode: release our permit and wait for exclusive to finish
            access.release()
            gateAfter.await()
        }
    }

    // do not call out of exclusiveMutex
    internal fun requestExclusive() {
        exclusiveGate = CompletableDeferred()
    }

    // do not call out of exclusiveMutex
    internal fun releaseExclusive() {
        exclusiveGate?.complete(Unit)
        exclusiveGate = null
    }

    suspend fun <T> withExclusive(block: suspend () -> T): T =
        exclusiveMutex.withLock {
            requestExclusive()
            access.acquireAllPermits()
            try {
                block()
            } finally {
                access.releaseAllPermits()
                releaseExclusive()
            }
        }
}
