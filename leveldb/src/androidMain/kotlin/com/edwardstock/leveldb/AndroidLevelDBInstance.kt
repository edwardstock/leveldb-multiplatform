package com.edwardstock.leveldb

import android.content.Context
import okio.Path.Companion.toPath
import java.io.File

/*
 * Copyright (c) 2025 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

class AndroidLevelDBInstance : LevelDBInstance {
    constructor(
        context: Context,
        dbName: String = LevelDB.DEFAULT_DBNAME,
        config: LevelDBConfig = LevelDBConfig(),
    ) : super(
        path = (context.filesDir.toString() + File.separator + dbName).toPath(),
        config = config
    )

    constructor(
        context: Context,
        dbName: String = LevelDB.DEFAULT_DBNAME,
        config: LevelDBConfig.() -> Unit,
    ) : super(
        path = (context.filesDir.toString() + File.separator + dbName).toPath(),
        config = LevelDBConfig().apply(config)
    )

}
