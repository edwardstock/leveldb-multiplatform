package com.edwardstock.leveldb

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