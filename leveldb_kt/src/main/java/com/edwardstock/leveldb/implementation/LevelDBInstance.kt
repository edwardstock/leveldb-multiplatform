package com.edwardstock.leveldb.implementation

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.exception.LevelDBException

/**
 * Singleshot DB opened.
 * Setup paths, configs etc and use db in special context blocks
 * @see leveldbContext
 */
open class LevelDBInstance {
    internal val path: String
    private val conf: LevelDB.Config

    constructor(dbPath: String, config: LevelDB.Config) {
        path = dbPath
        conf = config
    }

    constructor(dbPath: String, config: LevelDB.Config.() -> Unit) {
        path = dbPath
        conf = LevelDB.Config().apply(config)
    }

    @Throws(LevelDBException::class)
    fun <T> leveldbContext(block: LevelDB.() -> T) = LevelDB.open(path, conf).use { db ->
        db.block()
    }
}