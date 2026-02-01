package com.edwardstock.leveldb

import com.edwardstock.leveldb.internal.BytesHelper

/**
 * LevelDB-compatible lexicographic ordering for [ByteArray] keys.
 *
 * This is a small public API wrapper over internal comparator logic, so consumers and
 * test utilities can rely on the same ordering as the native implementation.
 */
object LevelDbByteArrayComparator : Comparator<ByteArray> {
    override fun compare(a: ByteArray, b: ByteArray): Int = BytesHelper.lexicographicCompare(a, b)
}

/**
 * Compares this [ByteArray] to [other] using LevelDB-compatible lexicographic ordering.
 */
fun ByteArray.lexicographicCompareTo(other: ByteArray): Int =
    BytesHelper.lexicographicCompare(this, other)
