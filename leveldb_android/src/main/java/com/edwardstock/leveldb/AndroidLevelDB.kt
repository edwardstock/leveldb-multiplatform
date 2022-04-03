package com.edwardstock.leveldb

import android.content.Context
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.implementation.*
import java.io.File

abstract class AndroidLevelDB(
    config: Config
) : LevelDB(config) {

    companion object {
        /**
         * Opens a new native (real) LevelDB at path with specified configuration.
         * @param context app context to use app files directory to store db
         * @param config configuration for the database, or null
         * @return a new [com.edwardstock.leveldb.implementation.NativeLevelDB]
         * @throws LevelDBException
         */
        @JvmStatic
        @Throws(LevelDBException::class)
        fun open(context: Context, config: Config): LevelDB {
            val path = context.filesDir.toString() + File.separator + DEFAULT_DBNAME
            return NativeLevelDB(path, config)
        }

        /**
         * Opens a new native (real) LevelDB at path with specified configuration.
         * @param context app context to use app files directory to store db
         * @param configuration configuration for the database
         * @return a new [com.edwardstock.leveldb.implementation.NativeLevelDB]
         * @throws LevelDBException
         */
        @Throws(LevelDBException::class)
        fun open(context: Context, config: Config.() -> Unit): LevelDB {
            val path = context.filesDir.toString() + File.separator + DEFAULT_DBNAME
            return NativeLevelDB(path, Config().apply(config))
        }

        /**
         * Convenience for [.open]
         * @param context app context to use app files directory to store db
         * @return a new [com.edwardstock.leveldb.implementation.NativeLevelDB] instance
         * @throws LevelDBException
         */
        @JvmStatic
        @Throws(LevelDBException::class)
        fun open(context: Context): LevelDB {
            val path = context.filesDir.toString() + File.separator + DEFAULT_DBNAME
            return open(path) {}
        }
    }

}
