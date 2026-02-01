/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.internal

import com.edwardstock.leveldb.JvmNativeLibraryLoader
import com.edwardstock.leveldb.NATIVE_LIB_NAME
import com.edwardstock.leveldb.api.ValueAdapter
import com.edwardstock.leveldb.impl.BigDecimalConverter
import com.edwardstock.leveldb.impl.BigIntegerConverter
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
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass

internal actual fun levelDbLoadNativeLibrary() {
    JvmNativeLibraryLoader.load(NATIVE_LIB_NAME)
}

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
    BigDecimal::class to BigDecimalConverter(),
    BigInteger::class to BigIntegerConverter(),
)

internal actual fun currentThreadName(): String = Thread.currentThread().name

internal actual fun stdoutPrintln(message: String) = println(message)
internal actual fun stderrPrintln(message: String) = System.err.println(message)
