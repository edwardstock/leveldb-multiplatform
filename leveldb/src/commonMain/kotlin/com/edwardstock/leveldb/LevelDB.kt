package com.edwardstock.leveldb

import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.exception.LevelDBNoTypeAdapterException
import com.edwardstock.leveldb.exception.LevelDBNotFoundException
import com.edwardstock.leveldb.exception.LevelDBSnapshotOwnershipException
import com.edwardstock.leveldb.implementation.NativeLevelDB
import kotlin.reflect.KClass

/*
 * Original author: Stojan Dimitrovski <sdimitrovski@gmail.com>
 * Modified by: Eduard Maximovich <edward.vstock@gmail.com>
 *
 * (Original BSD 3-Clause License follows)
 *
 * Copyright (c) 2014, Stojan Dimitrovski <sdimitrovski@gmail.com>
 *
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission
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
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 */

/**
 * Main LevelDB API for direct database access
 */
abstract class LevelDB(
    val config: LevelDBConfig,
) : AutoCloseable {

    companion object {
        const val DEFAULT_DBNAME = "default.ldb"

        val DEFAULT_FACTORY = LevelDBFactory { path, config -> NativeLevelDB(path, config) }

        init {
            levelDbLoadNativeLibrary()
        }
    }

    val token = Any()

    /**
     * Retrieves key from the database, possibly from a snapshot state
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param snapshot the snapshot from which to read the entry, may be null
     * @return data for the key, or null
     * @throws LevelDBException
     */
    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBException::class)
    abstract operator fun get(key: ByteArray, snapshot: Snapshot? = null): ByteArray?

    /**
     * Retrieves key from the database, possibly from a snapshot state
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param snapshot the snapshot from which to read the entry, may be null
     * @return data for the key, or null
     * @throws LevelDBException
     */
    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBException::class)
    operator fun get(key: String, snapshot: Snapshot? = null): ByteArray? {
        return get(key.encodeToByteArray(), snapshot)
    }

    /**
     * Retrieves key from the database, possibly from a snapshot state
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param snapshot the snapshot from which to read the entry, may be null
     * @return String data for the key, or null
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun getString(key: String, snapshot: Snapshot?): String? {
        val data = get(key, snapshot) ?: return null
        return data.decodeToString()
    }

    /**
     * Retrieves key from the database, possibly from a snapshot state
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @return String data for the key, or null
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun getString(key: String): String? {
        return getString(key, null)
    }

    @Throws(LevelDBNoTypeAdapterException::class)
    inline fun <reified T : Any> get(key: String): T? {
        return getString(key)?.let {
            config.convertT<T>(key, it)
        }
    }

    @Throws(LevelDBNoTypeAdapterException::class)
    fun <T : Any> get(key: String, clazz: KClass<T>): T? {
        return getString(key)?.let {
            config.convertT(key, it, clazz)
        }
    }

    /**
     * Retrieves key from the database with an implicit snapshot
     * @see .get
     */
    @Throws(LevelDBException::class)
    operator fun get(key: ByteArray): ByteArray? {
        return get(key, null)
    }

    /**
     * Retrieves key from the database with an implicit snapshot
     * @see .get
     */
    @Throws(LevelDBException::class)
    operator fun get(key: String): ByteArray? {
        return get(key, null)
    }

    /**
     * Raw form of [.getProperty]
     *
     *
     * Retrieves the LevelDB property entry specified with key
     * @param key non-null
     * @return property bytes
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    abstract fun getPropertyBytes(key: ByteArray): ByteArray?

    /**
     * Convenience function
     * @see com.edwardstock.leveldb.LevelDB.getPropertyBytes
     */
    @Throws(LevelDBClosedException::class)
    fun getProperty(key: ByteArray): String? {
        val value = getPropertyBytes(key) ?: return null
        return value.decodeToString()
    }

    /**
     * Convenience function
     * @see com.edwardstock.leveldb.LevelDB.getPropertyBytes
     */
    @Throws(LevelDBClosedException::class)
    fun getProperty(key: String): String? {
        return getProperty(key.encodeToByteArray())
    }

    /**
     * Creates a new [com.edwardstock.leveldb.LevelDBIterator] for this database
     *
     *
     * Data seen by the iterator will be consistent (like a snapshot). Closing the iterator is a must
     * The database implementation will not close iterators automatically when closed, which may
     * result in memory leaks
     * @param fillCache whether to fill the internal cache while iterating over the database
     * @param snapshot the snapshot from which to read the entries, may be null
     * @return new iterator
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBClosedException::class)
    abstract fun iterator(fillCache: Boolean = false, snapshot: Snapshot? = null): LevelDBIterator

    /**
     * Creates a new iterator that fills the cache
     * @return a new iterator
     * @throws com.edwardstock.leveldb.exception.LevelDBClosedException
     * @see .iterator
     */
    @Throws(LevelDBClosedException::class)
    open fun iterator(): LevelDBIterator {
        return iterator(true)
    }

    /**
     * Iterate over the database with an implicit snapshot created at the time of creation
     * of the iterator
     * @param fillCache whether to fill the internal cache while iterating over the database
     * @return a new iterator
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    open fun iterator(fillCache: Boolean = false): LevelDBIterator {
        return iterator(fillCache, null)
    }

    /**
     * Iterate over the entries from snapshot while filling the cache
     * @param snapshot the snapshot from which to read the entries, may be null
     * @return a new iterator
     * @throws LevelDBSnapshotOwnershipException
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBClosedException::class)
    fun iterator(snapshot: Snapshot? = null): LevelDBIterator {
        return iterator(true, snapshot)
    }

    /**
     * Obtains a new snapshot of this database's data
     *
     *
     * @return a new snapshot. Don't forget to call [.use] for auto-closable behavior or call [.close] on the snapshot
     */
    @Throws(LevelDBClosedException::class)
    abstract fun obtainSnapshot(): Snapshot

    /**
     * Writes the key-value pair in the database
     * @param key
     * @param value non-null, if null same as [.del]
     * @param sync whether this write will be forced to disk
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    abstract fun put(key: ByteArray, value: ByteArray?, sync: Boolean)

    /**
     * Writes the key-value pair in the database
     * @param key
     * @param value non-null, if null same as [.del]
     * @param sync whether this write will be forced to disk
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun put(key: String, value: String, sync: Boolean = false) {
        put(key.encodeToByteArray(), value.encodeToByteArray(), sync)
    }

    /**
     * Writes the key-value pair in the database
     * @param key
     * @param value non-null, if null same as [.del]
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun put(key: String, value: ByteArray?) {
        put(key.encodeToByteArray(), value, false)
    }

    /**
     * Asynchronous [.put]
     * @param key
     * @param value
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun put(key: ByteArray, value: ByteArray?) {
        put(key, value, false)
    }

    @Throws(LevelDBNoTypeAdapterException::class)
    inline fun <reified T : Any> put(key: String, value: T?) {
        value?.let {
            put(key, config.convertFromT(value))
        } ?: del(key)
    }

    @Throws(LevelDBNoTypeAdapterException::class)
    fun <T : Any> put(key: String, value: T?, clazz: KClass<T>) {
        value?.let {
            put(key, config.convertFromT(value, clazz))
        } ?: del(key)
    }

    /**
     * Writes a [com.edwardstock.leveldb.WriteBatch] to the database
     * @param writeBatch non-null, if null throws [java.lang.IllegalArgumentException]
     * @param sync whether this write will be forced to disk
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    abstract fun write(writeBatch: WriteBatch, sync: Boolean = false)

    /**
     * Deletes key from database, if it exists
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param sync whether this write will be forced to disk
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    abstract fun del(key: ByteArray, sync: Boolean)

    /**
     * Deletes key from database, if it exists
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun del(key: String) {
        del(key.encodeToByteArray(), false)
    }

    /**
     * Deletes key from database, if it exists
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param sync whether this write will be forced to disk
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun del(key: String, sync: Boolean) {
        del(key.encodeToByteArray(), sync)
    }

    /**
     * Asynchronous [.del]
     * @param key
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun del(key: ByteArray) {
        del(key, false)
    }

    /**
     * Start batch transaction
     * @param batch existing batch or null to create a new one
     * @param sync whether this write will be forced to disk
     * @param block code block with the batch operations
     */
    abstract fun withBatch(
        batch: WriteBatch? = null,
        sync: Boolean = true,
        block: WriteBatch.() -> Unit
    )

    /**
     * Closes this LevelDB instance. Database is usually not usable after a call to this method
     */
    abstract override fun close()


    /**
     * The path of this LevelDB. Usually a filesystem path, but may be something else
     * (eg: [com.edwardstock.leveldb.implementation.mock.MockLevelDB.getPath]
     * @return the path of this database, may be null
     */
    abstract var path: String
        protected set

    /**
     * Atomically check if this database has been closed
     * @return whether it's been closed
     */
    abstract val isClosed: Boolean

}
