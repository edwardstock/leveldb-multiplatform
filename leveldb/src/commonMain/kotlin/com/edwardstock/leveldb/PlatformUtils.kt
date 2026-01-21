package com.edwardstock.leveldb

import kotlin.reflect.KClass

/*
 * Copyright (c) 2025 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

internal expect fun levelDbLoadNativeLibrary()

internal expect fun levelDbDefaultAdapters(): MutableMap<KClass<*>, ValueAdapter<out Any>>

internal expect fun currentThreadName(): String
