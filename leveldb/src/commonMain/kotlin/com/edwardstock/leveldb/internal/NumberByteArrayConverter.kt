/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.internal

/**
 * Helper object for converting numbers to and from byte arrays
 * It uses big-endian order for byte representation
 */
internal object NumberByteArrayConverter {
    /**
     * Encodes a Long value into a ByteArray of a specified size
     * The value is truncated if it's larger than what the byteSize can hold
     */
    fun encode(value: Long, byteSize: Int): ByteArray {
        val byteArray = ByteArray(byteSize)
        for (i in 0 until byteSize) {
            byteArray[i] = (value shr (8 * (byteSize - 1 - i))).toByte()
        }
        return byteArray
    }

    /**
     * Decodes a ByteArray into a Long value
     * Assumes big-endian order
     */
    fun decode(byteArray: ByteArray, byteSize: Int): Long {
        var result = 0L
        for (i in 0 until byteSize) {
            result = (result shl 8) or (byteArray[i].toLong() and 0xFF)
        }
        return result
    }
}
