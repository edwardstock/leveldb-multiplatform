package com.edwardstock.leveldb

import com.edwardstock.leveldb.exception.LevelDBException

/*
 * Copyright (c) 2025 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

expect class DbHandle
expect class DbBatchHandle
expect class DbSnapshotHandle
expect class DbIteratorHandle

expect val NULL_DB_HANDLE: DbHandle
expect val NULL_DB_BATCH_HANDLE: DbBatchHandle
expect val NULL_DB_SNAPSHOT_HANDLE: DbSnapshotHandle
expect val NULL_DB_ITERATOR_HANDLE: DbIteratorHandle

expect object LevelDBNativeProvider { val impl: LevelDBNative }

/**
 * Low-level native binding used by platform implementations
 */
interface LevelDBNative {

    @Throws(LevelDBException::class)
    fun dbOpen(
        createIfMissing: Boolean = true,
        paranoidChecks: Boolean = false,
        cacheSize: Int = 0,
        blockSize: Int = 0,
        writeBufferSize: Int = 0,
        path: String,
    ): DbHandle

    /**
     * Free native pointer
     * Don't use [DbHandle] after calling this
     */
    fun dbClose(handle: DbHandle)

    /**
     * Natively writes key-value pair to the database. Pointer is unchecked
     * @param handle
     * @param sync
     * @param key
     * @param value
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun dbPut(handle: DbHandle, sync: Boolean, key: ByteArray, value: ByteArray)

    /**
     * Natively deletes key-value pair from the database. Pointer is unchecked
     * @param handle
     * @param sync
     * @param key
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun dbDelete(handle: DbHandle, sync: Boolean, key: ByteArray)

    @Throws(LevelDBException::class)
    fun dbWrite(handle: DbHandle, sync: Boolean, batchHandle: DbBatchHandle)

    /**
     * Natively retrieves key-value pair from the database. Pointer is unchecked
     * @param handle
     * @param key
     * @return
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun dbGet(handle: DbHandle, key: ByteArray, snapshotHandle: DbSnapshotHandle = NULL_DB_SNAPSHOT_HANDLE): ByteArray?

    /**
     * Natively gets LevelDB property. Pointer is unchecked
     * @param handle
     * @param key
     * @return
     */
    fun dbGetProperty(handle: DbHandle, key: ByteArray): ByteArray?

    /**
     * Natively destroys a database. Corresponds to: <tt>leveldb::DestroyDB()</tt>
     * @param path
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun dbDestroy(path: String)

    /**
     * Natively repairs a database. Corresponds to: <tt>leveldb::RepairDB()</tt>
     * @param path
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun dbRepair(path: String)

    /**
     * Natively creates a new iterator. Corresponds to <tt>leveldb::DB->NewIterator()</tt>
     * @param handle
     * @param fillCache
     * @return
     */
    @Throws(LevelDBException::class)
    fun dbIterate(handle: DbHandle, fillCache: Boolean, snapshotHandle: DbSnapshotHandle = NULL_DB_SNAPSHOT_HANDLE): DbIteratorHandle
    fun dbSnapshot(handle: DbHandle): DbSnapshotHandle
    fun dbReleaseSnapshot(handle: DbHandle, snapshotHandle: DbSnapshotHandle)


    /// ITERATOR

    fun iteratorClose(handle: DbIteratorHandle)
    fun iteratorValidate(handle: DbIteratorHandle): Boolean
    fun iteratorSeek(handle: DbIteratorHandle, key: ByteArray)
    fun iteratorSeekToFirst(handle: DbIteratorHandle)
    fun iteratorSeekToLast(handle: DbIteratorHandle)
    fun iteratorNext(handle: DbIteratorHandle)
    fun iteratorPrev(handle: DbIteratorHandle)
    fun iteratorKey(handle: DbIteratorHandle): ByteArray
    fun iteratorValue(handle: DbIteratorHandle): ByteArray

    /// BATCH

    /**
     * Native create. Corresponds to: <tt>new leveldb::SimpleWriteBatch()</tt>
     *
     * @return pointer to nat structure
     */
    fun batchCreate(): DbBatchHandle

    /**
     * Native SimpleWriteBatch put. Pointer is unchecked
     *
     * @param handle   nat structure pointer
     * @param key
     * @param value
     */
    fun batchPut(handle: DbBatchHandle, key: ByteArray, value: ByteArray)

    /**
     * Native SimpleWriteBatch delete. Pointer is unchecked
     *
     * @param handle nat structure pointer
     * @param key
     */
    fun batchDelete(handle: DbBatchHandle, key: ByteArray)

    /**
     * Native close. Releases all memory. Pointer is unchecked
     *
     * @param handle nat structure pointer
     */
    fun batchClose(handle: DbBatchHandle)
}
