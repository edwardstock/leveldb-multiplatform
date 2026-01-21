/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.WriteBatch
import com.edwardstock.leveldb.exception.LevelDBNoTypeAdapterException

internal class LevelDBWriterImpl(private val db: LevelDB) : LevelDBReaderImpl(db), LevelDBWriter {
    override fun put(key: ByteArray, value: ByteArray?, sync: Boolean) {
        db.put(key, value, sync)
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

    @Throws(LevelDBNoTypeAdapterException::class)
    inline fun <reified T : Any> put(key: String, value: T?) {
        value?.let {
            put(key, config.convertFromT(value))
        } ?: del(key)
    }
}
