/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.internal

import com.edwardstock.leveldb.exception.LevelDBException

actual typealias DbHandle = Long
actual typealias DbBatchHandle = Long
actual typealias DbSnapshotHandle = Long
actual typealias DbIteratorHandle = Long

actual val NULL_DB_HANDLE: DbHandle = 0L
actual val NULL_DB_BATCH_HANDLE: DbBatchHandle = 0L
actual val NULL_DB_ITERATOR_HANDLE: DbIteratorHandle = 0L
actual val NULL_DB_SNAPSHOT_HANDLE: DbSnapshotHandle = 0L


actual data object LevelDBNativeProvider : LevelDBNative {
    actual val impl: LevelDBNative = this

    @Throws(LevelDBException::class)
    external override fun dbOpen(
        createIfMissing: Boolean,
        paranoidChecks: Boolean,
        cacheSize: Int,
        blockSize: Int,
        writeBufferSize: Int,
        path: String,
    ): DbHandle

    @Throws(LevelDBException::class)
    external override fun dbDestroy(path: String)

    @Throws(LevelDBException::class)
    external override fun dbRepair(path: String)

    external override fun batchCreate(): DbBatchHandle

    external override fun batchClose(handle: DbBatchHandle)

    external override fun batchDelete(handle: DbBatchHandle, key: ByteArray)

    external override fun batchPut(handle: DbBatchHandle, key: ByteArray, value: ByteArray)

    external override fun iteratorValue(handle: DbIteratorHandle): ByteArray

    external override fun iteratorKey(handle: DbIteratorHandle): ByteArray

    external override fun iteratorPrev(handle: DbIteratorHandle)

    external override fun iteratorNext(handle: DbIteratorHandle)

    external override fun iteratorSeekToLast(handle: DbIteratorHandle)

    external override fun iteratorSeekToFirst(handle: DbIteratorHandle)

    external override fun iteratorSeek(handle: DbIteratorHandle, key: ByteArray)

    external override fun iteratorValidate(handle: DbIteratorHandle): Boolean

    external override fun iteratorClose(handle: DbIteratorHandle)

    external override fun dbReleaseSnapshot(handle: DbHandle, snapshotHandle: DbSnapshotHandle)

    external override fun dbSnapshot(handle: DbHandle): DbSnapshotHandle

    @Throws(LevelDBException::class)
    external override fun dbIterate(handle: DbHandle, fillCache: Boolean, snapshotHandle: DbSnapshotHandle): DbIteratorHandle

    external override fun dbGetProperty(handle: DbHandle, key: ByteArray): ByteArray?

    @Throws(LevelDBException::class)
    external override fun dbGet(handle: DbHandle, key: ByteArray, snapshotHandle: DbSnapshotHandle): ByteArray?

    @Throws(LevelDBException::class)
    external override fun dbWrite(handle: DbHandle, sync: Boolean, batchHandle: DbBatchHandle)

    @Throws(LevelDBException::class)
    external override fun dbDelete(handle: DbHandle, sync: Boolean, key: ByteArray)

    @Throws(LevelDBException::class)
    external override fun dbPut(handle: DbHandle, sync: Boolean, key: ByteArray, value: ByteArray)

    external override fun dbClose(handle: DbHandle)

}
