package com.edwardstock.leveldb

/*
 * Copyright (c) 2025 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

/**
 * Simple converter to convert data from and to string to custom type
 */
interface ValueAdapter<T> {
    /**
     * Decode value to user type
     */
    fun decode(key: ByteArray, value: ByteArray?): T?

    /**
     * Encode value to leveldb type
     */
    fun encode(value: T): ByteArray
}
