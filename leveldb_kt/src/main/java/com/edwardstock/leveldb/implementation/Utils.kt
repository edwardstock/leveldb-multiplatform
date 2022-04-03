package com.edwardstock.leveldb.implementation

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.exception.LevelDBException

/**
 * Use with care as it iterates over all data
 */
fun LevelDB.forEachAll(block: (String, String) -> Unit) {
    iterator().use {
        it.seekToFirst()
        while (it.isValid) {
            block(it.keyString(), it.valueString())
            it.next()
        }
    }
}

/**
 * Run your own code inside leveldb
 * Single-shot that helps you to open and close db automatically and only when it needs
 */
@Throws(LevelDBException::class)
fun <T> leveldbContext(db: LevelDBInstance, block: LevelDB.() -> T) = db.leveldbContext(block)