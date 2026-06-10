/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

@file:OptIn(ExperimentalForeignApi::class)

package com.edwardstock.leveldb.utils

import com.edwardstock.leveldb.exception.LevelDBCorruptionException
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.exception.LevelDBIOException
import com.edwardstock.leveldb.exception.LevelDBNotFoundException
import com.edwardstock.leveldb.interop.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped

internal fun cleveldb_status.leveldbStatusToException(message: String?): LevelDBException? = when(this) {
    LDB_OK -> null
    LDB_CORRUPTED_DATA -> LevelDBCorruptionException(message)
    LDB_IO_ERROR -> LevelDBIOException(message)
    LDB_NOT_FOUND -> LevelDBNotFoundException(message)
    LDB_MEMORY_ERROR -> LevelDBException("Failed to allocate memory: $message")
    LDB_INVALID_ARGUMENT -> LevelDBException(message)
    LDB_NOT_SUPPORTED -> LevelDBException("Operation is not supported: $message")
    LDB_UNKNOWN_ERROR -> LevelDBException("Unknown error $message")
    // LDB_INVALID_POINTER signals a null/invalid native handle; must not be swallowed as success.
    LDB_INVALID_POINTER -> LevelDBException("Invalid native pointer${message?.let { ": $it" } ?: ""}")
    else -> null
}

internal fun cleveldb_status.throwIfError(details: String? = null) {
    memScoped {
        val reader = allocStringReader()

        leveldb_last_error(reader.bufferPtr, reader.lenPtr)

        val message = reader.readString()
        val targetDetails = when {
            !message.isNullOrBlank() -> message
            !details.isNullOrBlank() -> details
            else -> null
        }
        leveldbStatusToException(targetDetails)?.let { throw it }
    }

}
