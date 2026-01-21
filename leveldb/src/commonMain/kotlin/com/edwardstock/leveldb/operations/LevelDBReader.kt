/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.LevelDBIterator
import com.edwardstock.leveldb.Snapshot
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.exception.LevelDBNoTypeAdapterException
import com.edwardstock.leveldb.exception.LevelDBSnapshotOwnershipException
import kotlin.reflect.KClass

/**
 * Read-only operations against a LevelDB instance
 */
interface LevelDBReader : LevelDBOps {
    /**
     * Retrieves key from the database, possibly from a snapshot state
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param snapshot the snapshot from which to read the entry, may be null
     * @return data for the key, or null
     * @throws LevelDBException
     */
    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBException::class)
    operator fun get(key: ByteArray, snapshot: Snapshot? = null): ByteArray?

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
    fun getPropertyBytes(key: ByteArray): ByteArray?

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
    fun iterator(fillCache: Boolean = false, snapshot: Snapshot? = null): LevelDBIterator

    /**
     * Creates a new iterator that fills the cache
     * @return a new iterator
     * @throws com.edwardstock.leveldb.exception.LevelDBClosedException
     * @see .iterator
     */
    @Throws(LevelDBClosedException::class)
    fun iterator(): LevelDBIterator {
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
    fun iterator(fillCache: Boolean = false): LevelDBIterator {
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
    fun obtainSnapshot(): Snapshot
}
