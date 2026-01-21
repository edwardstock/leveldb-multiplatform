/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb

/**
 * Factory for creating a LevelDB instance for a path and config
 */
fun interface LevelDBFactory {
    /**
     * Create a LevelDB instance for a path and config
     */
    operator fun invoke(path: String, config: LevelDBConfig): LevelDB
}
