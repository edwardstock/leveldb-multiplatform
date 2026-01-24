/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

@file:OptIn(ExperimentalForeignApi::class)

package com.edwardstock.leveldb

import cnames.structs.ndb_holder
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.interop.leveldb_batch_create
import com.edwardstock.leveldb.interop.leveldb_batch_delete
import com.edwardstock.leveldb.interop.leveldb_batch_put
import com.edwardstock.leveldb.interop.leveldb_delete
import com.edwardstock.leveldb.interop.leveldb_destroy
import com.edwardstock.leveldb.interop.leveldb_get
import com.edwardstock.leveldb.interop.leveldb_get_property
import com.edwardstock.leveldb.interop.leveldb_iter_key
import com.edwardstock.leveldb.interop.leveldb_iter_next
import com.edwardstock.leveldb.interop.leveldb_iter_prev
import com.edwardstock.leveldb.interop.leveldb_iter_seek
import com.edwardstock.leveldb.interop.leveldb_iter_seek_to_first
import com.edwardstock.leveldb.interop.leveldb_iter_seek_to_last
import com.edwardstock.leveldb.interop.leveldb_iter_valid
import com.edwardstock.leveldb.interop.leveldb_iter_value
import com.edwardstock.leveldb.interop.leveldb_iterate
import com.edwardstock.leveldb.interop.leveldb_open
import com.edwardstock.leveldb.interop.leveldb_put
import com.edwardstock.leveldb.interop.leveldb_repair
import com.edwardstock.leveldb.interop.leveldb_snapshot
import com.edwardstock.leveldb.interop.leveldb_write
import com.edwardstock.leveldb.utils.NativeDbBatchHandle
import com.edwardstock.leveldb.utils.NativeDbHandle
import com.edwardstock.leveldb.utils.NativeDbIteratorHandle
import com.edwardstock.leveldb.utils.NativeDbSnapshotHandle
import com.edwardstock.leveldb.utils.allocBufferReader
import com.edwardstock.leveldb.utils.pinScope
import com.edwardstock.leveldb.utils.readBytes
import com.edwardstock.leveldb.utils.throwIfError
import com.edwardstock.leveldb.utils.usize
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value


actual typealias DbHandle = NativeDbHandle
actual typealias DbBatchHandle = NativeDbBatchHandle
actual typealias DbSnapshotHandle = NativeDbSnapshotHandle
actual typealias DbIteratorHandle = NativeDbIteratorHandle

actual val NULL_DB_HANDLE: DbHandle = NativeDbHandle(null)
actual val NULL_DB_BATCH_HANDLE: DbBatchHandle = NativeDbBatchHandle(null)
actual val NULL_DB_ITERATOR_HANDLE: DbIteratorHandle = NativeDbIteratorHandle(null)
actual val NULL_DB_SNAPSHOT_HANDLE: DbSnapshotHandle = NativeDbSnapshotHandle(NULL_DB_HANDLE, null)

actual data object LevelDBNativeProvider : LevelDBNative {
    actual val impl: LevelDBNative = this

    override fun dbOpen(
        createIfMissing: Boolean,
        paranoidChecks: Boolean,
        cacheSize: Int,
        blockSize: Int,
        writeBufferSize: Int,
        path: String,
    ): DbHandle = memScoped {
        // out** for the handle
        val out = alloc<CPointerVar<ndb_holder>>()

        val status = leveldb_open(
            create_if_missing = createIfMissing,
            paranoid_checks = paranoidChecks,
            cache_size = cacheSize.toULong(),
            block_size = blockSize.toULong(),
            write_buffer_size = writeBufferSize.toULong(),
            db_path = path,
            out = out.ptr
        )

        status.throwIfError("leveldb_open failed")

        DbHandle(ptr = out.value ?: error("leveldb_open returned null handle"))
    }


    override fun dbClose(handle: DbHandle) = handle.close()


    override fun dbPut(handle: DbHandle, sync: Boolean, key: ByteArray, value: ByteArray): Unit = pinScope {
        val status = leveldb_put(
            holder = handle.requirePtr(),
            key = key.autoPinUByteArray(),
            key_len = key.size.toULong(),
            value = value.autoPinUByteArray(),
            value_len = value.size.toULong(),
            sync = sync
        )
        status.throwIfError()
    }

    /**
     * Natively deletes key-value pair from the database. Pointer is unchecked
     * @param handle
     * @param sync
     * @param key
     * @throws LevelDBException
     */
    override fun dbDelete(handle: DbHandle, sync: Boolean, key: ByteArray): Unit = pinScope {
        val status = leveldb_delete(
            holder = handle.requirePtr(),
            key = key.autoPinUByteArray(),
            key_len = key.usize,
            sync = sync
        )
        status.throwIfError()
    }

    override fun dbWrite(handle: DbHandle, sync: Boolean, batchHandle: DbBatchHandle): Unit = pinScope {
        val status = leveldb_write(
            holder = handle.requirePtr(),
            batch_holder = batchHandle.requirePtr(),
            sync = sync
        )
        status.throwIfError()
    }

    /**
     * Natively retrieves key-value pair from the database. Pointer is unchecked
     * @param handle
     * @param key
     * @return
     * @throws LevelDBException
     */
    override fun dbGet(handle: DbHandle, key: ByteArray, snapshotHandle: DbSnapshotHandle): ByteArray? = memScoped {
        val reader = allocBufferReader()
        val status = pinScope {
            leveldb_get(
                holder = handle.requirePtr(),
                key = key.autoPinUByteArray(),
                key_len = key.usize,
                snapshot_holder = snapshotHandle.ptr,
                out = reader.bufferPtr,
                out_len = reader.lenPtr
            )
        }
        status.throwIfError()

        reader.readBytes()
    }

    /**
     * Natively gets LevelDB property. Pointer is unchecked
     * @param handle
     * @param key
     * @return
     */
    override fun dbGetProperty(handle: DbHandle, key: ByteArray): ByteArray? = memScoped {
        val reader = allocBufferReader()
        val rc = pinScope {
            leveldb_get_property(
                holder = handle.ptr,
                key = key.autoPinUByteArray(),
                key_len = key.usize,
                out = reader.bufferPtr,
                out_len = reader.lenPtr
            )
        }
        rc.throwIfError()
        reader.readBytes()
    }

    /**
     * Natively destroys a database. Corresponds to: <tt>leveldb::DestroyDB()</tt>
     * @param path
     * @throws LevelDBException
     */
    override fun dbDestroy(path: String) {
        leveldb_destroy(path).throwIfError()
    }

    /**
     * Natively repairs a database. Corresponds to: <tt>leveldb::RepairDB()</tt>
     * @param path
     * @throws LevelDBException
     */
    override fun dbRepair(path: String) {
        leveldb_repair(path).throwIfError()
    }

    /**
     * Natively creates a new iterator. Corresponds to <tt>leveldb::DB->NewIterator()</tt>
     * @param handle
     * @param fillCache
     * @return
     */
    override fun dbIterate(handle: DbHandle, fillCache: Boolean, snapshotHandle: DbSnapshotHandle): DbIteratorHandle =
        memScoped {
            val out = leveldb_iterate(
                holder = handle.requirePtr(),
                fill_cache = fillCache,
                snapshot_holder = snapshotHandle.ptr
            )
            out?.let { DbIteratorHandle(it) } ?: throw LevelDBException("leveldb_iterate returned null handle")
        }

    override fun dbSnapshot(handle: DbHandle): DbSnapshotHandle = memScoped {
        val out = leveldb_snapshot(handle.requirePtr())
        out?.let { DbSnapshotHandle(handle, it) } ?: throw LevelDBException("leveldb_snapshot returned null handle")
    }

    override fun dbReleaseSnapshot(handle: DbHandle, snapshotHandle: DbSnapshotHandle) = snapshotHandle.close()

    // ITERATOR

    override fun iteratorClose(handle: DbIteratorHandle) {
        handle.close()
    }

    override fun iteratorValidate(handle: DbIteratorHandle): Boolean {
        return leveldb_iter_valid(handle.requirePtr()) == 1
    }

    override fun iteratorSeek(handle: DbIteratorHandle, key: ByteArray) = pinScope {
        leveldb_iter_seek(
            handle.requirePtr(),
            key = key.autoPinUByteArray(),
            key_len = key.usize
        )
    }

    override fun iteratorSeekToFirst(handle: DbIteratorHandle) {
        leveldb_iter_seek_to_first(
            it = handle.requirePtr()
        )
    }

    override fun iteratorSeekToLast(handle: DbIteratorHandle) {
        leveldb_iter_seek_to_last(handle.requirePtr())
    }

    override fun iteratorNext(handle: DbIteratorHandle) {
        leveldb_iter_next(handle.requirePtr())
    }

    override fun iteratorPrev(handle: DbIteratorHandle) {
        leveldb_iter_prev(handle.requirePtr())
    }

    override fun iteratorKey(handle: DbIteratorHandle): ByteArray = memScoped {
        val reader = allocBufferReader()
        val rc = leveldb_iter_key(
            it = handle.requirePtr(),
            out = reader.bufferPtr,
            out_len = reader.lenPtr
        )
        rc.throwIfError()

        reader.readBytes() ?: throw LevelDBException("leveldb_iter_key returned null")
    }

    override fun iteratorValue(handle: DbIteratorHandle): ByteArray = memScoped {
        val reader = allocBufferReader()

        val rc = leveldb_iter_value(
            it = handle.requirePtr(),
            out = reader.bufferPtr,
            out_len = reader.lenPtr
        )
        rc.throwIfError()

        reader.readBytes() ?: throw LevelDBException("leveldb_iter_key returned null")
    }

    override fun batchCreate(): DbBatchHandle {
        val out = leveldb_batch_create()
        return out?.let { DbBatchHandle(it) } ?: throw LevelDBException("leveldb_batch_create returned null handle")
    }

    override fun batchPut(handle: DbBatchHandle, key: ByteArray, value: ByteArray) = pinScope {
        leveldb_batch_put(
            batch = handle.requirePtr(),
            key = key.autoPinUByteArray(),
            key_len = key.usize,
            value = value.autoPinUByteArray(),
            value_len = value.usize
        )
    }

    override fun batchDelete(handle: DbBatchHandle, key: ByteArray) = pinScope {
        leveldb_batch_delete(
            batch = handle.requirePtr(),
            key = key.autoPinUByteArray(),
            key_len = key.usize
        )
    }

    override fun batchClose(handle: DbBatchHandle) {
        handle.close()
    }
}
