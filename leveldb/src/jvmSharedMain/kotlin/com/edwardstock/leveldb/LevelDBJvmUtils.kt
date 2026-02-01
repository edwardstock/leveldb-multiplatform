/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.internal.levelDbLoadNativeLibrary

internal const val NATIVE_LIB_NAME = "leveldb_jni"

/**
 * Load the native JNI library for JVM targets
 */
fun LevelDB.Companion.loadNative() = levelDbLoadNativeLibrary()
