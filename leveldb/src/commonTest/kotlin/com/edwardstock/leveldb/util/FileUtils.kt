/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.util

import okio.FileSystem
import okio.Path
import okio.SYSTEM

fun Path.exists(fs: FileSystem = FileSystem.SYSTEM): Boolean {
    return fs.exists(this)
}

val Path.absolutePath: String
    get() = normalized().toString()
