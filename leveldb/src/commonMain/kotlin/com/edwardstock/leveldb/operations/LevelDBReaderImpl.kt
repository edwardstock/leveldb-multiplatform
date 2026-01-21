/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.LevelDBConfig
import com.edwardstock.leveldb.LevelDBIterator
import com.edwardstock.leveldb.Snapshot
import com.edwardstock.leveldb.exception.LevelDBNoTypeAdapterException

internal open class LevelDBReaderImpl(private val db: LevelDB) : LevelDBReader {

    override val config: LevelDBConfig
        get() = db.config

    override fun get(key: ByteArray, snapshot: Snapshot?): ByteArray? {
        return db[key, snapshot]
    }

    @Throws(LevelDBNoTypeAdapterException::class)
    inline fun <reified T : Any> getAny(key: String): T? {
        return getString(key)?.let {
            config.convertT<T>(key, it)
        }
    }

    override fun getPropertyBytes(key: ByteArray): ByteArray? {
        return db.getPropertyBytes(key)
    }

    override fun iterator(fillCache: Boolean, snapshot: Snapshot?): LevelDBIterator {
        return db.iterator(fillCache, snapshot)
    }

    override fun obtainSnapshot(): Snapshot {
        return db.obtainSnapshot()
    }
}
