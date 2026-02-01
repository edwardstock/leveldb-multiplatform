package com.edwardstock.leveldb.internal

import kotlinx.coroutines.sync.Semaphore

internal const val MAX_DB_ACCESS_PERMITS = 256

internal suspend fun Semaphore.acquireAllPermits() {
    var acquired = 0
    try {
        repeat(MAX_DB_ACCESS_PERMITS) {
            acquire()
            acquired++
        }
    } catch (t: Throwable) {
        repeat(acquired) { release() }
        throw t
    }
}

internal fun Semaphore.releaseAllPermits() {
    repeat(MAX_DB_ACCESS_PERMITS) {
        this.release()
    }
}
