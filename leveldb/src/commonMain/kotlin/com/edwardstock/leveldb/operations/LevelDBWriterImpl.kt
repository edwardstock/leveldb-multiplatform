/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.LevelDBIterator
import com.edwardstock.leveldb.Snapshot
import com.edwardstock.leveldb.WriteBatch
import com.edwardstock.leveldb.exception.LevelDBNoTypeAdapterException
import kotlin.reflect.KClass

internal class LevelDBOpsImpl(private val db: LevelDB) : LevelDBOps {
    override val config = db.config

    override fun get(key: ByteArray, snapshot: Snapshot?): ByteArray? = db[key, snapshot]

    @Throws(LevelDBNoTypeAdapterException::class)
    inline fun <reified T : Any> getAny(key: String): T? =
        getString(key)?.let { config.convertT<T>(key, it) }

    override fun getPropertyBytes(key: ByteArray): ByteArray? = db.getPropertyBytes(key)

    override fun iterator(fillCache: Boolean, snapshot: Snapshot?): LevelDBIterator =
        db.iterator(fillCache, snapshot)

    override fun obtainSnapshot(): Snapshot = db.obtainSnapshot()

    override fun put(key: ByteArray, value: ByteArray?, sync: Boolean) {
        db.put(key, value, sync)
    }

    @Throws(LevelDBNoTypeAdapterException::class)
    inline fun <reified T : Any> put(key: String, value: T?) {
        value?.let { put(key, config.convertFromT(value)) } ?: del(key)
    }

    override fun write(writeBatch: WriteBatch, sync: Boolean) {
        db.write(writeBatch, sync)
    }

    override fun del(key: ByteArray, sync: Boolean) {
        db.del(key, sync)
    }

    override fun withBatch(batch: WriteBatch?, sync: Boolean, block: WriteBatch.() -> Unit) {
        db.withBatch(batch, sync, block)
    }

    override fun <T : Any> put(key: String, value: T?, clazz: KClass<T>) {
        value?.let { put(key, config.convertFromT(value, clazz)) } ?: del(key)
    }
}
