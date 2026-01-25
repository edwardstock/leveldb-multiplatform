package com.edwardstock.leveldb.utils

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.LevelDBConfig
import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.implementation.NativeLevelDB
import com.edwardstock.leveldb.operations.LevelDBOps
import okio.Path
import kotlin.coroutines.cancellation.CancellationException

/*
 * Copyright (c) 2025 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

/**
 * Use with care as it iterates over all data
 */
fun LevelDBOps.forEachAll(block: (String, String) -> Unit) {
    iterator().use {
        it.seekToFirst()
        while (it.isValid) {
            block(it.keyString(), it.valueString())
            it.next()
        }
    }
}

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
 * Iterate over all existing keys
 * It'a faster than [forEachAll] as it doesn't get values from slice
 */
fun LevelDBOps.forEachKeys(block: (String) -> Unit) {
    iterator().use {
        it.seekToFirst()
        while (it.isValid) {
            block(it.keyString())
            it.next()
        }
    }
}
fun LevelDB.forEachKeys(block: (String) -> Unit) {
    iterator().use {
        it.seekToFirst()
        while (it.isValid) {
            block(it.keyString())
            it.next()
        }
    }
}

/**
 * Iterate over all existing values
 * It'a faster than [forEachAll] as it doesn't get keys from slice
 */
fun LevelDBOps.forEachValues(block: (String) -> Unit) {
    iterator().use {
        it.seekToFirst()
        while (it.isValid) {
            block(it.valueString())
            it.next()
        }
    }
}
/**
 * Iterate over all existing values
 * It'a faster than [forEachAll] as it doesn't get keys from slice
 */
fun LevelDB.forEachValues(block: (String) -> Unit) {
    iterator().use {
        it.seekToFirst()
        while (it.isValid) {
            block(it.valueString())
            it.next()
        }
    }
}

/**
 * Run your own code inside leveldb
 * Single-shot that helps you to open and close db automatically and only when it needs
 */
@Throws(LevelDBException::class, CancellationException::class)
suspend fun <T> use(db: LevelDBInstance, block: suspend LevelDBOps.() -> T) =
    db.use(block)

/**
 * Convenience for [.open]
 * @param path the path to the database
 * @return a new [com.edwardstock.leveldb.implementation.NativeLevelDB] instance
 * @throws LevelDBException
 */
@Throws(LevelDBException::class)
fun LevelDB.Companion.open(path: String, config: LevelDBConfig.() -> Unit): LevelDB {
    return NativeLevelDB(path, LevelDBConfig().apply(config))
}

@Throws(LevelDBException::class)
fun LevelDB.Companion.open(path: String, config: LevelDBConfig): LevelDB {
    return NativeLevelDB(path, config)
}

fun LevelDB.Companion.open(path: Path, config: LevelDBConfig): LevelDB = open(path.normalized().toString(), config)

/**
 * Destroys the contents of a LevelDB database
 * @param path the path to the database
 * @throws com.edwardstock.leveldb.exception.LevelDBException
 * @see com.edwardstock.leveldb.implementation.NativeLevelDB.destroy
 */
@Throws(LevelDBException::class)
fun LevelDB.Companion.destroy(path: String) {
    NativeLevelDB.destroy(path)
}

/**
 * If a DB cannot be opened, you may attempt to call this method to resurrect as much of the contents of the
 * database as possible. Some data may be lost, so be careful when calling this function on a database that contains
 * important information
 * @param path the path to the database
 * @throws com.edwardstock.leveldb.exception.LevelDBException
 * @see com.edwardstock.leveldb.implementation.NativeLevelDB.repair
 */
@Throws(LevelDBException::class)
fun LevelDB.Companion.repair(path: String) {
    NativeLevelDB.repair(path)
}
