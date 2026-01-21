/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.LevelDBConfig

/**
 * Shared config holder for LevelDB accessors
 */
interface LevelDBOps {
    val config: LevelDBConfig
}
