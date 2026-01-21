package com.edwardstock.leveldb.implementation

import com.edwardstock.leveldb.ValueAdapter
import com.edwardstock.leveldb.utils.NumberByteArrayConverter
import java.math.BigDecimal
import java.math.BigInteger

/*
 * Copyright (c) 2025 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

class BigIntegerConverter : ValueAdapter<BigInteger> {
    override fun decode(key: ByteArray, value: ByteArray?): BigInteger? {
        return value?.let { BigInteger(it) }
    }

    override fun encode(value: BigInteger): ByteArray {
        return value.toByteArray()
    }
}

class BigDecimalConverter : ValueAdapter<BigDecimal> {
    override fun decode(key: ByteArray, value: ByteArray?): BigDecimal? {
        return value?.let { bytes ->
            if (bytes.size < 4) return null // Need at least 4 bytes for the scale

            val scaleBytes = bytes.copyOfRange(0, 4)
            val scale = NumberByteArrayConverter.decode(scaleBytes, 4).toInt()

            val unscaledValueBytes = bytes.copyOfRange(4, bytes.size)
            val unscaledValue = BigInteger(unscaledValueBytes)

            BigDecimal(unscaledValue, scale)
        }
    }

    override fun encode(value: BigDecimal): ByteArray {
        val scaleBytes = NumberByteArrayConverter.encode(value.scale().toLong(), 4)
        val unscaledValueBytes = value.unscaledValue().toByteArray()
        return scaleBytes + unscaledValueBytes
    }
}
