/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.LevelDBConfig
import com.edwardstock.leveldb.LevelDBIterator
import com.edwardstock.leveldb.Snapshot
import com.edwardstock.leveldb.WriteBatch
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.exception.LevelDBIteratorNotValidException
import com.edwardstock.leveldb.exception.LevelDBNoTypeAdapterException
import com.edwardstock.leveldb.exception.LevelDBSnapshotOwnershipException
import kotlin.reflect.KClass

/**
 * Unified LevelDB operations facade (read + write).
 */
interface LevelDBOps {
    val config: LevelDBConfig

    // ----- Reads -----
    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBException::class)
    operator fun get(key: ByteArray, snapshot: Snapshot? = null): ByteArray?

    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBException::class)
    operator fun get(key: String, snapshot: Snapshot? = null): ByteArray? =
        get(key.encodeToByteArray(), snapshot)

    @Throws(LevelDBException::class)
    operator fun get(key: ByteArray): ByteArray? = get(key, null)

    @Throws(LevelDBException::class)
    operator fun get(key: String): ByteArray? = get(key, null)

    @Throws(LevelDBException::class)
    fun getString(key: String, snapshot: Snapshot?): String? = get(key, snapshot)?.decodeToString()

    @Throws(LevelDBException::class)
    fun getString(key: String): String? = getString(key, null)

    @Throws(LevelDBNoTypeAdapterException::class)
    fun <T : Any> get(key: String, clazz: KClass<T>): T? =
        getString(key)?.let { config.convertT(key, it, clazz) }

    @Throws(LevelDBIteratorNotValidException::class, LevelDBClosedException::class)
    fun iterator(fillCache: Boolean = false, snapshot: Snapshot? = null): LevelDBIterator

    @Throws(LevelDBClosedException::class)
    fun iterator(): LevelDBIterator = iterator(true)

    @Throws(LevelDBClosedException::class)
    fun iterator(fillCache: Boolean = false): LevelDBIterator = iterator(fillCache, null)

    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBClosedException::class)
    fun iterator(snapshot: Snapshot? = null): LevelDBIterator = iterator(true, snapshot)

    @Throws(LevelDBClosedException::class)
    fun obtainSnapshot(): Snapshot

    @Throws(LevelDBClosedException::class)
    fun getPropertyBytes(key: ByteArray): ByteArray?

    @Throws(LevelDBClosedException::class)
    fun getProperty(key: ByteArray): String? = getPropertyBytes(key)?.decodeToString()

    @Throws(LevelDBClosedException::class)
    fun getProperty(key: String): String? = getProperty(key.encodeToByteArray())

    // ----- Writes -----
    @Throws(LevelDBException::class)
    fun put(key: ByteArray, value: ByteArray?, sync: Boolean = false)

    @Throws(LevelDBException::class)
    fun put(key: String, value: String, sync: Boolean = false) =
        put(key.encodeToByteArray(), value.encodeToByteArray(), sync)

    @Throws(LevelDBException::class)
    fun put(key: String, value: ByteArray?) = put(key.encodeToByteArray(), value, false)

    @Throws(LevelDBException::class, LevelDBNoTypeAdapterException::class)
    fun <T : Any> put(key: String, value: T?, clazz: KClass<T>) {
        value?.let { put(key, config.convertFromT(value, clazz)) } ?: del(key)
    }

    @Throws(LevelDBException::class)
    fun write(writeBatch: WriteBatch, sync: Boolean = false)

    @Throws(LevelDBException::class)
    fun del(key: ByteArray, sync: Boolean = false)

    @Throws(LevelDBException::class)
    fun del(key: String) = del(key.encodeToByteArray(), false)

    @Throws(LevelDBException::class)
    fun del(key: String, sync: Boolean) = del(key.encodeToByteArray(), sync)

    @Throws(LevelDBException::class)
    fun del(key: ByteArray) = del(key, false)

    fun withBatch(
        batch: WriteBatch? = null,
        sync: Boolean = true,
        block: WriteBatch.() -> Unit,
    )
}
