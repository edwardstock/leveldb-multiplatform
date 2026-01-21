/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.WriteBatch
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.exception.LevelDBNoTypeAdapterException
import kotlin.reflect.KClass

/**
 * Read/write operations against a LevelDB instance
 */
interface LevelDBWriter : LevelDBReader {
    /**
     * Writes the key-value pair in the database
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param value non-null, if null same as [.del]
     * @param sync whether this write will be forced to disk
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun put(key: ByteArray, value: ByteArray?, sync: Boolean)

    /**
     * Writes the key-value pair in the database
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
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
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
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
    fun write(writeBatch: WriteBatch, sync: Boolean = false)

    /**
     * Deletes key from database, if it exists
     * @param key non-null, if null throws [java.lang.IllegalArgumentException]
     * @param sync whether this write will be forced to disk
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun del(key: ByteArray, sync: Boolean)

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
    fun withBatch(
        batch: WriteBatch? = null,
        sync: Boolean = true,
        block: WriteBatch.() -> Unit
    )
}
