package com.edwardstock.leveldb.util

import com.edwardstock.leveldb.utils.NumberByteArrayConverter
import kotlin.test.Test
import kotlin.test.assertEquals

class NumberByteArrayConverterTest {

    @Test
    fun `encode decode round trip for typical sizes`() {
        val cases = listOf(
            1 to listOf(0L, 1L, 0xFFL),
            2 to listOf(0L, 1L, 0xFFFFL),
            4 to listOf(0L, 1L, 0xFFFFFFFFL),
            8 to listOf(0L, 1L, 0x1122334455667788L)
        )

        for ((size, values) in cases) {
            for (value in values) {
                val encoded = NumberByteArrayConverter.encode(value, size)
                val decoded = NumberByteArrayConverter.decode(encoded, size)
                assertEquals(value, decoded)
            }
        }
    }

    @Test
    fun `encode truncates values that exceed size`() {
        val encoded = NumberByteArrayConverter.encode(0x1FFFFL, 2)
        val decoded = NumberByteArrayConverter.decode(encoded, 2)
        assertEquals(0xFFFFL, decoded)
    }
}
