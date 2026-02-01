package com.edwardstock.leveldb.impl

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class BigNumberConvertersTest {
    @Test
    fun `big integer converter round trip`() {
        val converter = BigIntegerConverter()
        val value = BigInteger("123456789012345678901234567890")
        val decoded = converter.decode(converter.encode(value))
        assertEquals(value, decoded)
    }

    @Test
    fun `big decimal converter round trip`() {
        val converter = BigDecimalConverter()
        val value = BigDecimal("12345.6789")
        val decoded = converter.decode(converter.encode(value))
        assertEquals(value, decoded)
    }
}
