/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

@file:OptIn(ExperimentalForeignApi::class)

package com.edwardstock.leveldb

import com.edwardstock.leveldb.implementation.BoolConverter
import com.edwardstock.leveldb.implementation.ByteConverter
import com.edwardstock.leveldb.implementation.CharConverter
import com.edwardstock.leveldb.implementation.DoubleConverter
import com.edwardstock.leveldb.implementation.FloatConverter
import com.edwardstock.leveldb.implementation.IntConverter
import com.edwardstock.leveldb.implementation.LongConverter
import com.edwardstock.leveldb.implementation.ShortConverter
import com.edwardstock.leveldb.implementation.UByteConverter
import com.edwardstock.leveldb.implementation.UIntConverter
import com.edwardstock.leveldb.implementation.ULongConverter
import com.edwardstock.leveldb.implementation.UShortConverter
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.pthread_self
import kotlin.reflect.KClass

internal actual fun levelDbLoadNativeLibrary() = Unit

internal actual fun levelDbDefaultAdapters(): MutableMap<KClass<*>, ValueAdapter<out Any>> = mutableMapOf(
    Float::class to FloatConverter(),
    Double::class to DoubleConverter(),
    Boolean::class to BoolConverter(),
    Byte::class to ByteConverter(),
    UByte::class to UByteConverter(),
    Char::class to CharConverter(),
    Short::class to ShortConverter(),
    UShort::class to UShortConverter(),
    Int::class to IntConverter(),
    UInt::class to UIntConverter(),
    Long::class to LongConverter(),
    ULong::class to ULongConverter(),
)

internal actual fun currentThreadName(): String {
    // Portable fallback for non-Apple targets
    return "thread-${pthread_self()}"
}
