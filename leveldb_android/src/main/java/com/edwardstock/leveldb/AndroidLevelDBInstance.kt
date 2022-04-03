package com.edwardstock.leveldb

import android.content.Context
import com.edwardstock.leveldb.implementation.LevelDBInstance
import java.io.File

class AndroidLevelDBInstance : LevelDBInstance {
    constructor(
        context: Context,
        dbName: String? = null,
        config: LevelDB.Config = LevelDB.Config()
    ) : super(
        dbPath = context.filesDir.toString() + File.separator + (dbName ?: LevelDB.DEFAULT_DBNAME),
        config = config
    )

    constructor(
        context: Context,
        dbName: String? = null,
        config: LevelDB.Config.() -> Unit
    ) : super(
        dbPath = context.filesDir.toString() + File.separator + (dbName ?: LevelDB.DEFAULT_DBNAME),
        config = LevelDB.Config().apply(config)
    )

}
