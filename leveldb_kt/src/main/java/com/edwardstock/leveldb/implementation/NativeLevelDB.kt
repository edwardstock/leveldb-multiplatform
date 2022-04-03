package com.edwardstock.leveldb.implementation

import com.edwardstock.leveldb.Iterator
import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.Snapshot
import com.edwardstock.leveldb.WriteBatch
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.exception.LevelDBSnapshotOwnershipException
import java.util.concurrent.atomic.AtomicLong

/*
 * Stojan Dimitrovski
 *
 * Copyright (c) 2014, Stojan Dimitrovski <sdimitrovski@gmail.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OFz SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/**
 * Object for interacting with the native LevelDB implementation.
 */
class NativeLevelDB(
    filePath: String,
    config: Config
) : LevelDB(config) {

    /**
     * This is the underlying pointer. If you touch this, all hell breaks loose and everyone dies.
     */
    private val ref: AtomicLong = AtomicLong(0L)
    private val refValue: Long
        get() {
            assert(ref.get() != 0L) { "Getting null pointer of LevelDB" }
            return ref.get()
        }

    /**
     * Checks whether this database has been closed.
     * @return true if closed, false if not
     */
    override val isClosed: Boolean
        get() = ref.get() == 0L

    /**
     * The path that this database has been opened with.
     * Set the path which opens this database.
     */
    @Volatile
    override var path: String = filePath

    init {
        ref.set(
            nopen(
                config.createIfMissing,
                config.cacheSize,
                config.blockSize,
                config.writeBufferSize,
                path
            )
        )
    }

    /**
     * Closes this database, i.e. releases nat resources. You may call this multiple times. You cannot use any other
     * method on this object after closing it.
     */
    override fun close() {
        if (ref.get() != 0L) {
            nclose(refValue)
            ref.set(0L)
        }
    }

    /**
     * Writes a key-value record to the database. Wirting can be synchronous or asynchronous.
     *
     *
     * Asynchronous writes will be buffered to the kernel before this function returns. This guarantees data consistency
     * even if the process crashes or is killed, but not if the system crashes.
     *
     *
     * Synchronous writes block everything until data gets written to disk. Data is secure even if the system crashes.
     * @param key the key (usually a string, but bytes are the way LevelDB stores things)
     * @param value the value. Null value will delete item
     * @param sync whether this is a synchronous (true) or asynchronous (false) write
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    override fun put(key: ByteArray, value: ByteArray?, sync: Boolean) {
        value?.let {
            checkIfClosed()
            nput(refValue, sync, key, value)
        } ?: del(key, sync)
    }

    /**
     * Writes a [com.edwardstock.leveldb.WriteBatch] to the database.
     * @param writeBatch the WriteBatch to write
     * @param sync whether this is a synchronous (true) or asynchronous (false) write
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    override fun write(writeBatch: WriteBatch, sync: Boolean) {
        checkIfClosed()
        NativeWriteBatch(writeBatch).use { batch ->
            nwrite(refValue, sync, batch.nativePointer())
        }
    }

    /**
     * Gets the value associated with the key, or <tt>null</tt>.
     * @param key the key
     * @param snapshot the snapshot from which to read the pair, or null
     * @return the value, or <tt>null</tt>
     * @throws LevelDBException
     */
    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBException::class)
    override fun get(key: ByteArray, snapshot: Snapshot?): ByteArray? {
        if (snapshot != null) {
            if (snapshot !is NativeSnapshot) {
                throw LevelDBSnapshotOwnershipException()
            }
            if (!snapshot.checkOwner(this)) {
                throw LevelDBSnapshotOwnershipException()
            }
        }
        checkIfClosed()
        return nget(refValue, key, if (snapshot == null) 0 else (snapshot as NativeSnapshot).id())
    }

    /**
     * Deletes the specified entry from the database. Deletion can be synchronous or asynchronous.
     * @param key the key
     * @param sync whether this is a synchronous (true) or asynchronous (false) delete
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    override fun del(key: ByteArray, sync: Boolean) {
        checkIfClosed()
        ndelete(refValue, sync, key)
    }

    /**
     * Get a property of LevelDB, or null.
     *
     *
     * Valid property names include:
     *
     *   * "leveldb.num-files-at-level<N>" - return the number of files at level <N>, where <N> is an ASCII
     * representation of a level number (e.g. "0").</N></N></N>
     *
     *  * "leveldb.stats" - returns a multi-line string that describes statistics about the internal operation of the
     * DB.
     *
     *  * "leveldb.sstables" - returns a multi-line string that describes all of the sstables that make up the db
     * contents.
     *
     *
     * @param key the key
     * @return property data, or <tt>null</tt>
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    override fun getPropertyBytes(key: ByteArray): ByteArray? {
        checkIfClosed()
        return ngetProperty(refValue, key)
    }

    /**
     * Creates a new [com.edwardstock.leveldb.Iterator] that iterates over this database.
     *
     *
     * The returned iterator is not thread safe and must be closed with [com.edwardstock.leveldb.Iterator.close] before closing this
     * database.
     * @param fillCache whether iterating fills the internal cache
     * @return a new iterator
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBClosedException::class)
    override fun iterator(fillCache: Boolean, snapshot: Snapshot?): Iterator {
        if (snapshot != null) {
            if (snapshot !is NativeSnapshot) {
                throw LevelDBSnapshotOwnershipException()
            }
            if (snapshot.checkOwner(this)) {
                throw LevelDBSnapshotOwnershipException()
            }
        }
        checkIfClosed()
        return NativeIterator(
            niterate(
                refValue,
                fillCache,
                if (snapshot == null) 0 else (snapshot as NativeSnapshot).id()
            )
        )
    }

    @Throws(LevelDBClosedException::class)
    override fun obtainSnapshot(): Snapshot {
        return NativeSnapshot(this, nsnapshot(refValue))
    }

    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBClosedException::class)
    override fun releaseSnapshot(snapshot: Snapshot?) {
        requireNotNull(snapshot) { "Snapshot must not be null." }
        if (snapshot !is NativeSnapshot) {
            throw LevelDBSnapshotOwnershipException()
        }
        if (!snapshot.checkOwner(this)) {
            throw LevelDBSnapshotOwnershipException()
        }
        checkIfClosed()
        nreleaseSnapshot(refValue, snapshot.release())
    }

    /**
     * Checks if this database has been closed. If it has, throws a [com.edwardstock.leveldb.exception.LevelDBClosedException].
     *
     *
     * Use before calling any of the nat functions that require the ndb pointer.
     *
     *
     * Don't call this outside a synchronized context.
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    protected fun checkIfClosed() {
        if (isClosed) {
            throw LevelDBClosedException()
        }
    }

    companion object {
        init {
            loadNative()
        }

        /**
         * @see com.edwardstock.leveldb.LevelDB.destroy
         */
        @Throws(LevelDBException::class)
        fun destroy(path: String) {
            ndestroy(path)
        }

        /**
         * @see com.edwardstock.leveldb.LevelDB.repair
         */
        @Throws(LevelDBException::class)
        fun repair(path: String) {
            nrepair(path)
        }

        // NATIVE METHODS
        /**
         * Natively opens the database.
         * @param createIfMissing
         * @param path
         * @return the nat structure pointer
         * @throws LevelDBException
         */
        @Throws(LevelDBException::class)
        private external fun nopen(
            createIfMissing: Boolean,
            cacheSize: Int,
            blockSize: Int,
            writeBufferSize: Int,
            path: String
        ): Long

        /**
         * Natively closes pointers and memory. Pointer is unchecked.
         * @param ndb
         */
        private external fun nclose(ndb: Long)

        /**
         * Natively writes key-value pair to the database. Pointer is unchecked.
         * @param ndb
         * @param sync
         * @param key
         * @param value
         * @throws LevelDBException
         */
        @Throws(LevelDBException::class)
        private external fun nput(ndb: Long, sync: Boolean, key: ByteArray, value: ByteArray)

        /**
         * Natively deletes key-value pair from the database. Pointer is unchecked.
         * @param ndb
         * @param sync
         * @param key
         * @throws LevelDBException
         */
        @Throws(LevelDBException::class)
        private external fun ndelete(ndb: Long, sync: Boolean, key: ByteArray)

        @Throws(LevelDBException::class)
        private external fun nwrite(ndb: Long, sync: Boolean, nwb: Long)

        /**
         * Natively retrieves key-value pair from the database. Pointer is unchecked.
         * @param ndb
         * @param key
         * @return
         * @throws LevelDBException
         */
        @Throws(LevelDBException::class)
        private external fun nget(ndb: Long, key: ByteArray, nsnapshot: Long): ByteArray?

        /**
         * Natively gets LevelDB property. Pointer is unchecked.
         * @param ndb
         * @param key
         * @return
         */
        private external fun ngetProperty(ndb: Long, key: ByteArray): ByteArray?

        /**
         * Natively destroys a database. Corresponds to: <tt>leveldb::DestroyDB()</tt>
         * @param path
         * @throws LevelDBException
         */
        @Throws(LevelDBException::class)
        private external fun ndestroy(path: String)

        /**
         * Natively repairs a database. Corresponds to: <tt>leveldb::RepairDB()</tt>
         * @param path
         * @throws LevelDBException
         */
        @Throws(LevelDBException::class)
        private external fun nrepair(path: String)

        /**
         * Natively creates a new iterator. Corresponds to <tt>leveldb::DB->NewIterator()</tt>.
         * @param ndb
         * @param fillCache
         * @return
         */
        private external fun niterate(ndb: Long, fillCache: Boolean, nsnapshot: Long): Long
        private external fun nsnapshot(ndb: Long): Long
        private external fun nreleaseSnapshot(ndb: Long, nsnapshot: Long)
    }

}