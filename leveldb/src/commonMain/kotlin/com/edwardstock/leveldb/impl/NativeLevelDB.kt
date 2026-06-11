/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.LevelDBIterator
import com.edwardstock.leveldb.api.Snapshot
import com.edwardstock.leveldb.api.WriteBatch
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.exception.LevelDBNotFoundException
import com.edwardstock.leveldb.exception.LevelDBSnapshotOwnershipException
import com.edwardstock.leveldb.internal.DbHandle
import com.edwardstock.leveldb.internal.LevelDBNativeProvider
import com.edwardstock.leveldb.internal.NULL_DB_SNAPSHOT_HANDLE
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlin.concurrent.Volatile

/**
 * Object for interacting with the native LevelDB implementation
 *
 * Thread-safety: DB operations are thread-safe; iterator and snapshot instances are not and must not
 * be shared across threads without external synchronization
 */
class NativeLevelDB(
    filePath: String,
    override val config: LevelDBInstanceConfig = LevelDBInstanceConfig(),
) : LevelDB {
    private val snapshotsLock = reentrantLock()
    private val snapshots = mutableSetOf<NativeSnapshot>()

    /**
     * This is the underlying pointer. If you touch this, all hell breaks loose and everyone dies
     */
    private var _handle by atomic<DbHandle?>(null)
    private val handle get() = _handle ?: throw LevelDBClosedException("DB is closed")

    /**
     * Checks whether this database has been closed
     * @return true if closed, false if not
     */
    override val isClosed: Boolean
        get() = _handle == null

    /**
     * The path that this database has been opened with
     * Set the path which opens this database
     */
    @Volatile
    private var path: String = filePath

    init {
        _handle = LevelDBNativeProvider.impl.dbOpen(
            createIfMissing = config.driver.createIfMissing,
            paranoidChecks = config.driver.paranoidChecks,
            cacheSize = config.driver.cacheSize,
            blockSize = config.driver.blockSize,
            writeBufferSize = config.driver.writeBufferSize,
            path = path
        )
    }

    /**
     * Closes this database, i.e. releases nat resources. You may call this multiple times. You cannot use any other
     * method on this object after closing it
     */
    override fun close() {
        if (_handle != null) {
            val toClose = snapshotsLock.withLock {
                val current = snapshots.toList()
                snapshots.clear()
                current
            }
            toClose.forEach { it.close() }

            LevelDBNativeProvider.impl.dbClose(handle)
            _handle = null
        }
    }

    /**
     * Writes a key-value record to the database. Wirting can be synchronous or asynchronous
     *
     *
     * Asynchronous writes will be buffered to the kernel before this function returns. This guarantees data consistency
     * even if the process crashes or is killed, but not if the system crashes
     *
     *
     * Synchronous writes block everything until data gets written to disk. Data is secure even if the system crashes
     * @param key the key (usually a string, but bytes are the way LevelDB stores things)
     * @param value the value. Null value will delete item
     * @param sync whether this is a synchronous (true) or asynchronous (false) write
     * @throws LevelDBException
     */
    override fun putBytes(key: ByteArray, value: ByteArray?, sync: Boolean) {
        value?.let {
            checkIfClosed()
            LevelDBNativeProvider.impl.dbPut(handle, sync, key, value)
        } ?: del(key, sync)
    }

    /**
     * Writes a [com.edwardstock.leveldb.WriteBatch] to the database
     * @param writeBatch the WriteBatch to write
     * @param sync whether this is a synchronous (true) or asynchronous (false) write
     * @throws LevelDBException
     */
    override fun write(writeBatch: WriteBatch, sync: Boolean) {
        checkIfClosed()
        NativeWriteBatch(writeBatch).use { batch ->
            LevelDBNativeProvider.impl.dbWrite(handle, sync, batch.nativePointer())
        }
    }

    /**
     * Gets the value associated with the key, or <tt>null</tt>
     * @param key the key
     * @param snapshot the snapshot from which to read the data, or null
     * @return the value, or <tt>null</tt>
     * @throws LevelDBException
     */
    override fun getBytes(key: ByteArray, snapshot: Snapshot?): ByteArray? {
        snapshot.ensureOwnership()
        checkIfClosed()

        return try {
            LevelDBNativeProvider.impl.dbGet(
                handle = handle,
                key = key,
                snapshotHandle = when (snapshot) {
                    is NativeSnapshot -> snapshot.handle ?: NULL_DB_SNAPSHOT_HANDLE
                    else -> NULL_DB_SNAPSHOT_HANDLE
                }
            )
        } catch (_: LevelDBNotFoundException) {
            null
        }
    }

    override fun del(key: ByteArray, sync: Boolean) {
        checkIfClosed()
        try {
            LevelDBNativeProvider.impl.dbDelete(handle = handle, sync = sync, key = key)
        } catch (_: LevelDBNotFoundException) {
            // ignore
        }
    }

    override fun withBatch(
        batch: WriteBatch?,
        sync: Boolean,
        block: WriteBatch.() -> Unit,
    ) {
        val targetBatch = (batch ?: SimpleWriteBatch(config))
        targetBatch.apply(block)
        write(targetBatch, sync)
    }

    /**
     * Get a property of LevelDB, or null
     *
     *
     * Valid property names include:
     *
     *   * "leveldb.num-files-at-level<N>" - return the number of files at level <N>, where <N> is an ASCII
     * representation of a level number (e.g. "0").</N></N></N>
     *
     *  * "leveldb.stats" - returns a multi-line string that describes statistics about the internal operation of the
     * DB
     *
     *  * "leveldb.sstables" - returns a multi-line string that describes all of the sstables that make up the db
     * contents
     *
     *
     * @param key the key
     * @return property data, or <tt>null</tt>
     * @throws LevelDBClosedException
     */
    override fun getPropertyBytes(key: ByteArray): ByteArray? {
        checkIfClosed()
        return LevelDBNativeProvider.impl.dbGetProperty(handle, key)
    }

    /**
     * Creates a new [com.edwardstock.leveldb.LevelDBIterator] that iterates over this database
     *
     *
     * The returned iterator is not thread safe and must be closed with [com.edwardstock.leveldb.LevelDBIterator.close] before closing this
     * database
     * @param fillCache whether iterating fills the internal cache
     * @return a new iterator
     * @throws LevelDBClosedException
     */
    override fun iterator(fillCache: Boolean, snapshot: Snapshot?): LevelDBIterator {
        snapshot.ensureOwnership()
        checkIfClosed()
        return NativeIterator(
            handle = LevelDBNativeProvider.impl.dbIterate(
                handle = handle,
                fillCache = fillCache,
                snapshotHandle = (snapshot as? NativeSnapshot)?.handle ?: NULL_DB_SNAPSHOT_HANDLE
            ),
            config = config
        )
    }

    override fun obtainSnapshot(): Snapshot {
        checkIfClosed()
        return NativeSnapshot(
            ownerToken = this,
            snapshotHandle = LevelDBNativeProvider.impl.dbSnapshot(handle),
            onClosed = {
                snapshotsLock.withLock { snapshots.remove(it) }
            },
            releaseNative = { snapshotHandle ->
                checkIfClosed()
                LevelDBNativeProvider.impl.dbReleaseSnapshot(handle, snapshotHandle)
            }
        ).also { snapshot ->
            snapshotsLock.withLock { snapshots.add(snapshot) }
        }
    }

    /**
     * Checks if this database has been closed. If it has, throws a [com.edwardstock.leveldb.exception.LevelDBClosedException]
     *
     *
     * Use before calling any of the nat functions that require the ndb pointer
     *
     *
     * Don't call this outside a synchronized context
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    private fun checkIfClosed() {
        if (isClosed) {
            throw LevelDBClosedException()
        }
    }

    private fun Snapshot?.ensureOwnership() {
        if (this == null) return
        if (this !is NativeSnapshot || !this.checkOwner(this@NativeLevelDB)) {
            throw LevelDBSnapshotOwnershipException()
        }
    }

    companion object {
        /**
         * @see com.edwardstock.leveldb.LevelDB.destroy
         */
        @Throws(LevelDBException::class)
        fun destroy(path: String) = LevelDBNativeProvider.impl.dbDestroy(path)

        /**
         * @see com.edwardstock.leveldb.LevelDB.repair
         */
        @Throws(LevelDBException::class)
        fun repair(path: String) = LevelDBNativeProvider.impl.dbRepair(path)

    }

}
