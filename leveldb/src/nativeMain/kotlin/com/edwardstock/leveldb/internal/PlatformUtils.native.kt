/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

@file:OptIn(ExperimentalForeignApi::class)

package com.edwardstock.leveldb.internal

import com.edwardstock.leveldb.api.ValueAdapter
import com.edwardstock.leveldb.impl.BoolConverter
import com.edwardstock.leveldb.impl.ByteConverter
import com.edwardstock.leveldb.impl.CharConverter
import com.edwardstock.leveldb.impl.DoubleConverter
import com.edwardstock.leveldb.impl.FloatConverter
import com.edwardstock.leveldb.impl.IntConverter
import com.edwardstock.leveldb.impl.LongConverter
import com.edwardstock.leveldb.impl.ShortConverter
import com.edwardstock.leveldb.impl.UByteConverter
import com.edwardstock.leveldb.impl.UIntConverter
import com.edwardstock.leveldb.impl.ULongConverter
import com.edwardstock.leveldb.impl.UShortConverter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pin
import platform.posix.fflush
import platform.posix.fprintf
import platform.posix.pthread_self
import platform.posix.stderr
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
    return "thread-${pthread_self()?.pin()?.get()}"
}

internal actual fun stdoutPrintln(message: String) = println(message)

internal actual fun stderrPrintln(message: String) {
    fprintf(stderr, "%s\n", message)
    fflush(stderr)
}
