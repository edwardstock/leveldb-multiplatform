package com.edwardstock.leveldb

import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.exception.LevelDBNoTypeAdapterException
import com.edwardstock.leveldb.exception.LevelDBSnapshotOwnershipException
import com.edwardstock.leveldb.implementation.*
import com.edwardstock.leveldb.implementation.mock.MockLevelDB
import java.io.Closeable
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass

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

abstract class LevelDB(
    val config: Config
) : Closeable, AutoCloseable {

    companion object {
        const val DEFAULT_DBNAME = "default.ldb"
        const val NATIVE_LIB_NAME = "leveldb_jni"

        @JvmStatic
        fun loadNative() {
            System.loadLibrary(NATIVE_LIB_NAME)
        }

        /**
         * Convenience for [.open]
         * @param path the path to the database
         * @return a new [com.edwardstock.leveldb.implementation.NativeLevelDB] instance
         * @throws LevelDBException
         */
        @Throws(LevelDBException::class)
        fun open(path: String, config: Config.() -> Unit): LevelDB {
            return NativeLevelDB(path, Config().apply(config))
        }

        @Throws(LevelDBException::class)
        fun open(path: String, config: Config): LevelDB {
            return NativeLevelDB(path, config)
        }

        /**
         * Creates a new [com.edwardstock.leveldb.implementation.mock.MockLevelDB] useful in
         * testing in non-Android environments such as Robolectric. It does not access the filesystem,
         * and is in-memory only.
         * @return a new [com.edwardstock.leveldb.implementation.mock.MockLevelDB]
         */
        @JvmStatic
        fun mock(): LevelDB {
            return MockLevelDB()
        }

        /**
         * Destroys the contents of a LevelDB database.
         * @param path the path to the database
         * @throws com.edwardstock.leveldb.exception.LevelDBException
         * @see com.edwardstock.leveldb.implementation.NativeLevelDB.destroy
         */
        @JvmStatic
        @Throws(LevelDBException::class)
        fun destroy(path: String) {
            NativeLevelDB.destroy(path)
        }

        /**
         * If a DB cannot be opened, you may attempt to call this method to resurrect as much of the contents of the
         * database as possible. Some data may be lost, so be careful when calling this function on a database that contains
         * important information.
         * @param path the path to the database
         * @throws com.edwardstock.leveldb.exception.LevelDBException
         * @see com.edwardstock.leveldb.implementation.NativeLevelDB.repair
         */
        @JvmStatic
        @Throws(LevelDBException::class)
        fun repair(path: String) {
            NativeLevelDB.repair(path)
        }
    }

    /**
     * Closes this LevelDB instance. Database is usually not usable after a call to this method.
     */
    abstract override fun close()

    /**
     * Writes the key-value pair in the database.
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param value non-null, if null same as [.del]
     * @param sync whether this write will be forced to disk
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    abstract fun put(key: ByteArray, value: ByteArray?, sync: Boolean)

    /**
     * Writes the key-value pair in the database.
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param value non-null, if null same as [.del]
     * @param sync whether this write will be forced to disk
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    @JvmOverloads
    fun put(key: String, value: String, sync: Boolean = false) {
        put(key.toByteArray(), value.toByteArray(), sync)
    }

    /**
     * Writes the key-value pair in the database.
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param value non-null, if null same as [.del]
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun put(key: String, value: ByteArray?) {
        put(key.toByteArray(), value, false)
    }

    /**
     * Asynchronous [.put].
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
     * Writes a [com.edwardstock.leveldb.WriteBatch] to the database.
     * @param writeBatch non-null, if null throws [java.lang.IllegalArgumentException]
     * @param sync whether this write will be forced to disk
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    abstract fun write(writeBatch: WriteBatch, sync: Boolean)

    /**
     * Asynchronous [.write].
     * @param writeBatch
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun write(writeBatch: WriteBatch) {
        write(writeBatch, false)
    }

    /**
     * Retrieves key from the database, possibly from a snapshot state.
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param snapshot the snapshot from which to read the entry, may be null
     * @return data for the key, or null
     * @throws LevelDBException
     */
    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBException::class)
    abstract operator fun get(key: ByteArray, snapshot: Snapshot? = null): ByteArray?

    /**
     * Retrieves key from the database, possibly from a snapshot state.
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param snapshot the snapshot from which to read the entry, may be null
     * @return data for the key, or null
     * @throws LevelDBException
     */
    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBException::class)
    operator fun get(key: String, snapshot: Snapshot? = null): ByteArray? {
        return get(key.toByteArray(), snapshot)
    }

    /**
     * Retrieves key from the database, possibly from a snapshot state.
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param snapshot the snapshot from which to read the entry, may be null
     * @return String data for the key, or null
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun getString(key: String, snapshot: Snapshot?): String? {
        val data = get(key, snapshot) ?: return null
        return String(data)
    }

    /**
     * Retrieves key from the database, possibly from a snapshot state.
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
     * Retrieves key from the database with an implicit snapshot.
     * @see .get
     */
    @Throws(LevelDBException::class)
    operator fun get(key: ByteArray): ByteArray? {
        return get(key, null)
    }

    /**
     * Retrieves key from the database with an implicit snapshot.
     * @see .get
     */
    @Throws(LevelDBException::class)
    operator fun get(key: String): ByteArray? {
        return get(key, null)
    }

    /**
     * Deletes key from database, if it exists.
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param sync whether this write will be forced to disk
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    abstract fun del(key: ByteArray, sync: Boolean)

    /**
     * Deletes key from database, if it exists.
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun del(key: String) {
        del(key.toByteArray(), false)
    }

    /**
     * Deletes key from database, if it exists.
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param sync whether this write will be forced to disk
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun del(key: String, sync: Boolean) {
        del(key.toByteArray(), sync)
    }

    /**
     * Asynchronous [.del].
     * @param key
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun del(key: ByteArray) {
        del(key, false)
    }

    /**
     * Raw form of [.getProperty].
     *
     *
     * Retrieves the LevelDB property entry specified with key.
     * @param key non-null
     * @return property bytes
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    abstract fun getPropertyBytes(key: ByteArray): ByteArray?

    /**
     * Convenience function.
     * @see com.edwardstock.leveldb.LevelDB.getPropertyBytes
     */
    @Throws(LevelDBClosedException::class)
    fun getProperty(key: ByteArray): String? {
        val value = getPropertyBytes(key) ?: return null
        return String(value)
    }

    /**
     * Convenience function.
     * @see com.edwardstock.leveldb.LevelDB.getPropertyBytes
     */
    @Throws(LevelDBClosedException::class)
    fun getProperty(key: String): String? {
        return getProperty(key.toByteArray())
    }

    /**
     * Creates a new [com.edwardstock.leveldb.Iterator] for this database.
     *
     *
     * Data seen by the iterator will be consistent (like a snapshot). Closing the iterator is a must.
     * The database implementation will not close iterators automatically when closed, which may
     * result in memory leaks.
     * @param fillCache whether to fill the internal cache while iterating over the database
     * @param snapshot the snapshot from which to read the entries, may be null
     * @return new iterator
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBClosedException::class)
    abstract fun iterator(fillCache: Boolean = false, snapshot: Snapshot? = null): Iterator

    /**
     * Creates a new iterator that fills the cache.
     * @return a new iterator
     * @throws com.edwardstock.leveldb.exception.LevelDBClosedException
     * @see .iterator
     */
    @Throws(LevelDBClosedException::class)
    open fun iterator(): Iterator {
        return iterator(true)
    }

    /**
     * Iterate over the database with an implicit snapshot created at the time of creation
     * of the iterator.
     * @param fillCache whether to fill the internal cache while iterating over the database
     * @return a new iterator
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    open fun iterator(fillCache: Boolean = false): Iterator {
        return iterator(fillCache, null)
    }

    /**
     * Iterate over the entries from snapshot while filling the cache.
     * @param snapshot the snapshot from which to read the entries, may be null
     * @return a new iterator
     * @throws LevelDBSnapshotOwnershipException
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBClosedException::class)
    fun iterator(snapshot: Snapshot? = null): Iterator {
        return iterator(true, snapshot)
    }


    /**
     * The path of this LevelDB. Usually a filesystem path, but may be something else
     * (eg: [com.edwardstock.leveldb.implementation.mock.MockLevelDB.getPath].
     * @return the path of this database, may be null
     */
    abstract var path: String
        protected set

    /**
     * Atomically check if this database has been closed.
     * @return whether it's been closed
     */
    abstract val isClosed: Boolean

    /**
     * Obtains a new snapshot of this database's data.
     *
     *
     * Make sure you call [.releaseSnapshot] when you are done with it.
     * @return a new snapshot
     */
    @Throws(LevelDBClosedException::class)
    abstract fun obtainSnapshot(): Snapshot?

    /**
     * Releases a previously obtained snapshot. It is not an error to release a snapshot
     * multiple times.
     *
     * If this database does not own the snapshot, a [com.edwardstock.leveldb.exception.LevelDBSnapshotOwnershipException]
     * will be thrown at runtime.
     * @param snapshot the snapshot to release, if null throws a [java.lang.IllegalArgumentException]
     */
    @Throws(
        LevelDBSnapshotOwnershipException::class,
        LevelDBClosedException::class,
        IllegalArgumentException::class
    )
    abstract fun releaseSnapshot(snapshot: Snapshot?)

    /**
     * Specifies a configuration to open the database with.
     *
     * @param createIfMissing If true, the database will be created if it is missing.
     * @param cacheSize Maximum cache size for fillCache parameter
     * @param blockSize Approximate size of user data packed per block.
     * Note that the block size specified here corresponds to uncompressed data.
     * The actual size of the unit read from disk may be smaller if
     * compression is enabled.  This parameter can be changed dynamically.
     * @param writeBufferSize Parameters that affect performance
     * Amount of data to build up in memory (backed by an unsorted log
     * on disk) before converting to a sorted on-disk file.
     *
     * Larger values increase performance, especially during bulk loads.
     * Up to two write buffers may be held in memory at the same time,
     * so you may wish to adjust this parameter to control memory usage.
     * Also, a larger write buffer will result in a longer recovery time
     * the next time the database is opened.
     *
     * @param adapters data mapper for user types
     */
    data class Config(
        var createIfMissing: Boolean = true,
        var cacheSize: Int = 0,
        var blockSize: Int = 0,
        var writeBufferSize: Int = 0,
        var adapters: MutableMap<KClass<*>, ValueAdapter<*>> = mutableMapOf(
            Float::class to FloatConverter(),
            Double::class to DoubleConverter(),
            BigDecimal::class to BigDecimalConverter(),
            Boolean::class to BoolConverter(),
            Byte::class to ByteConverter(),
            UByte::class to UByteConverter(),
            Char::class to CharConverter(),
            Short::class to ShortConverter(),
            UShort::class to UShortConverter(),
            Int::class to IntConverter(),
            UInt::class to UIntConverter(),
            Long::class to LongConverter(),
            ULong::class to ULongConverter(),
            BigInteger::class to BigIntegerConverter(),
        )
    ) {

        @Suppress("UNCHECKED_CAST")
        inline fun <reified T : Any> addAdapter(adapter: ValueAdapter<T>) {
            adapters[T::class] = adapter
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> addAdapter(clazz: KClass<T>, adapter: ValueAdapter<T>) {
            adapters[clazz] = adapter
        }

        @Suppress("UNCHECKED_CAST")
        inline fun <reified T> findAdapter(): ValueAdapter<T>? {
            return adapters[T::class] as? ValueAdapter<T>
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> findAdapter(clazz: KClass<T>): ValueAdapter<T>? {
            return adapters[clazz] as? ValueAdapter<T>
        }

        inline fun <reified T> convertT(key: String, value: String): T {
            return findAdapter<T>()?.decode(key.toByteArray(), value.toByteArray())
                ?: throw LevelDBNoTypeAdapterException(T::class)
        }

        @SuppressWarnings("unchecked")
        fun <T : Any> convertT(key: String, value: String, clazz: KClass<T>): T {
            return findAdapter(clazz)?.decode(key.toByteArray(), value.toByteArray())
                ?: throw LevelDBNoTypeAdapterException(clazz)
        }

        inline fun <reified T : Any> convertFromT(value: T): String {
            return findAdapter<T>()?.encode(value)?.toString()
                ?: throw LevelDBNoTypeAdapterException(T::class)
        }

        fun <T : Any> convertFromT(value: T, clazz: KClass<T>): String {
            return findAdapter(clazz)?.encode(value)?.toString()
                ?: throw LevelDBNoTypeAdapterException(clazz)
        }
    }


}