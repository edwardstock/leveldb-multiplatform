/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

@file:OptIn(ExperimentalForeignApi::class)

package com.edwardstock.leveldb.utils

import com.edwardstock.leveldb.interop.leveldb_free_buffer
import com.edwardstock.leveldb.interop.leveldb_free_string
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import platform.posix.size_tVar

class MemScopeBufferReader<T: CPointed>(
    val buffer: CPointerVar<T>,
    val len: size_tVar
) {
    val bufferPtr = buffer.ptr
    val lenPtr = len.ptr
}

fun MemScopeBufferReader<UByteVar>.readBytes(): ByteArray? {
    val size = len.value.toInt()
    if (size == 0) {
        return ByteArray(0)
    }
    val bufferValue = buffer.value ?: return null
    return try {
        bufferValue.readBytes(size)
    } finally {
        leveldb_free_buffer(bufferValue)
    }
}

fun MemScopeBufferReader<ByteVar>.readString(): String? {
    val size = len.value.toInt()
    if (size == 0) {
        return ""
    }
    val bufferValue = buffer.value ?: return null
    return try {
        val byteArray = bufferValue.readBytes(size)
        byteArray.decodeToString()
    } finally {
        leveldb_free_string(bufferValue)
    }
}

fun MemScope.allocBufferReader(): MemScopeBufferReader<UByteVar> = MemScopeBufferReader(
    buffer = alloc<CPointerVar<UByteVar>>(),
    len = alloc<size_tVar>()
)

fun MemScope.allocStringReader(): MemScopeBufferReader<ByteVar> = MemScopeBufferReader(
    buffer = alloc<CPointerVar<ByteVar>>(),
    len = alloc<size_tVar>()
)
