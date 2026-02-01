package com.edwardstock.leveldb

import android.content.Context
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.config.LevelDBInstanceConfig.CloseStrategy
import com.edwardstock.leveldb.migration.LevelDBSchema
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.io.File
import kotlin.time.TimeSource

/*
 * Copyright (c) 2025 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

class AndroidLevelDBInstance private constructor(
    path: Path,
    config: LevelDBInstanceConfig,
    fileSystem: FileSystem,
    closeStrategy: CloseStrategy = CloseStrategy.Immediate,
    timeSource: TimeSource = TimeSource.Monotonic,
    schema: LevelDBSchema? = null,
    scope: CoroutineScope,
) : LevelDBInstance(
    path = path,
    config = config,
    fileSystem = fileSystem,
    closeStrategy = closeStrategy,
    timeSource = timeSource,
    schema = schema,
    scope = scope
) {
    companion object {
        /**
         * Starts an Android-friendly builder that resolves the DB under [context.filesDir].
         */
        fun builder(context: Context, dbName: String = LevelDB.DEFAULT_DBNAME) =
            Builder(context, dbName)

        /**
         * DSL variant of [builder] that applies [block] before building.
         */
        fun builder(
            context: Context,
            dbName: String = LevelDB.DEFAULT_DBNAME,
            block: Builder.() -> Unit,
        ): AndroidLevelDBInstance =
            Builder(context, dbName).apply(block).build()
    }

    class Builder(
        context: Context,
        dbName: String = LevelDB.DEFAULT_DBNAME,
        private var baseDir: File = context.filesDir,
    ) : LevelDBInstance.Builder(baseDir.resolve(dbName).absolutePath.toPath()) {
        private var name: String = dbName

        /** Sets a custom database name while keeping [baseDir] in sync. */
        fun dbName(name: String) = apply {
            this.name = name
            path(baseDir.resolve(name).absolutePath.toPath())
        }

        /** Changes the base directory and recomputes the resolved path. */
        fun baseDirectory(directory: File) = apply {
            this.baseDir = directory
            path(baseDir.resolve(name).absolutePath.toPath())
        }

        /** Path overload of [baseDirectory]. */
        fun baseDirectory(directory: Path) = apply {
            baseDirectory(directory.toFile())
        }

        override fun build(): AndroidLevelDBInstance = super.build() as AndroidLevelDBInstance

        override fun createInstance(
            path: Path,
            config: LevelDBInstanceConfig,
            fileSystem: FileSystem,
            scope: CoroutineScope,
        ): LevelDBInstance = AndroidLevelDBInstance(
            path = path,
            config = config,
            fileSystem = fileSystem,
            scope = scope
        )
    }
}
