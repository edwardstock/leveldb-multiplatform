/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.api

import com.edwardstock.leveldb.config.LevelDBInstanceConfig

/**
 * Factory for creating a LevelDB instance for a path and config
 */
fun interface LevelDBInstanceFactory {
    /**
     * Create a LevelDB instance for a path and config
     */
    operator fun invoke(
        path: String,
        config: LevelDBInstanceConfig,
    ): LevelDB
}
