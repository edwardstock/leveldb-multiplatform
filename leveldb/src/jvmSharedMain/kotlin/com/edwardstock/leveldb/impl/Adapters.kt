package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.api.ValueAdapter
import com.edwardstock.leveldb.exception.LevelDBDecodingException
import com.edwardstock.leveldb.internal.NumberByteArrayConverter
import java.math.BigDecimal
import java.math.BigInteger

/*
 * Copyright (c) 2025 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

class BigIntegerConverter : ValueAdapter<BigInteger> {
    override fun decode(value: ByteArray): BigInteger {
        return BigInteger(value)
    }

    override fun encode(value: BigInteger): ByteArray {
        return value.toByteArray()
    }
}

class BigDecimalConverter : ValueAdapter<BigDecimal> {
    override fun decode(value: ByteArray): BigDecimal {
        return value.let { bytes ->
            if (bytes.size < 4) {
                throw LevelDBDecodingException(
                    "Cannot decode BigDecimal from byte array: insufficient data. " +
                            "Expected size must be at least 4 bytes, but got ${bytes.size} bytes."
                )
            }

            val scaleBytes = bytes.copyOfRange(0, 4)
            // decode returns unsigned Long, toInt() restores int32 raw bits (including negative values)
            val scale = NumberByteArrayConverter.decode(scaleBytes, 4).toInt()

            val unscaledValueBytes = bytes.copyOfRange(4, bytes.size)
            val unscaledValue = BigInteger(unscaledValueBytes)

            BigDecimal(unscaledValue, scale)
        }
    }

    override fun encode(value: BigDecimal): ByteArray {
        // scale stored as int32 raw bits (two’s complement), big-endian
        val scaleBytes = NumberByteArrayConverter.encode(value.scale().toLong(), 4)
        val unscaledValueBytes = value.unscaledValue().toByteArray()
        return scaleBytes + unscaledValueBytes
    }
}
