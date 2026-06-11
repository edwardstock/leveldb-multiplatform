package com.edwardstock.leveldb.util

import com.edwardstock.leveldb.internal.NumberByteArrayConverter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class NumberByteArrayConverterTest {

    @Test
    fun `encode produces big-endian byte layout`() {
        // Most significant byte first. If the implementation switched to
        // little-endian, encode(1, 4) would be [1,0,0,0] and this fails.
        assertContentEquals(byteArrayOf(0, 0, 0, 1), NumberByteArrayConverter.encode(1L, 4))
        assertContentEquals(byteArrayOf(0, 1), NumberByteArrayConverter.encode(1L, 2))
        assertContentEquals(byteArrayOf(1), NumberByteArrayConverter.encode(1L, 1))

        // Distinct bytes pin every position and the ordering between them.
        assertContentEquals(
            byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x78),
            NumberByteArrayConverter.encode(0x1122334455667778L, 8),
        )

        // High bit set in the most-significant byte: pins sign-bit handling
        // and proves the top byte is at index 0 (big-endian), not index 3.
        assertContentEquals(
            byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x01),
            NumberByteArrayConverter.encode(0xFF000001L, 4),
        )
    }

    @Test
    fun `decode reads big-endian byte layout`() {
        // [0,0,0,1] must decode to 1, not 0x01000000. Catches a little-endian
        // decode regression that a round-trip-only test would miss.
        assertEquals(1L, NumberByteArrayConverter.decode(byteArrayOf(0, 0, 0, 1), 4))
        assertEquals(
            0x1122334455667778L,
            NumberByteArrayConverter.decode(
                byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x78), 8,
            ),
        )
        // Top byte 0xFF must land in the high position, decoded unsigned.
        assertEquals(0xFF000001L, NumberByteArrayConverter.decode(byteArrayOf(0xFF.toByte(), 0, 0, 1), 4))
    }

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

    @Test
    fun `encode pins 8-byte Long boundary extremes`() {
        // Long.MIN_VALUE == 0x8000000000000000: only the MSB set, at index 0.
        // -1L (all-FF) does NOT isolate the sign bit; this does.
        assertContentEquals(
            byteArrayOf(0x80.toByte(), 0, 0, 0, 0, 0, 0, 0),
            NumberByteArrayConverter.encode(Long.MIN_VALUE, 8),
        )
        // Long.MAX_VALUE == 0x7FFFFFFFFFFFFFFF: sign bit clear, rest all-FF.
        assertContentEquals(
            byteArrayOf(0x7F, -1, -1, -1, -1, -1, -1, -1),
            NumberByteArrayConverter.encode(Long.MAX_VALUE, 8),
        )
        // -1L == 0xFFFFFFFFFFFFFFFF: every byte 0xFF (two's complement).
        assertContentEquals(
            byteArrayOf(-1, -1, -1, -1, -1, -1, -1, -1),
            NumberByteArrayConverter.encode(-1L, 8),
        )
        // Zero: all-zero bytes.
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            NumberByteArrayConverter.encode(0L, 8),
        )
    }

    @Test
    fun `decode of all-FF 8 bytes is signed minus one and round-trips Long extremes`() {
        // The raw converter returns a signed Long. 8 bytes of 0xFF must decode
        // to -1L. The `and 0xFF` mask per byte is what keeps this correct; a
        // mutation dropping the mask would sign-extend each byte and corrupt
        // this (e.g. yield 0 or a garbage value).
        assertEquals(-1L, NumberByteArrayConverter.decode(byteArrayOf(-1, -1, -1, -1, -1, -1, -1, -1), 8))
        // Boundary round trips through encode -> decode at 8 bytes.
        assertEquals(Long.MIN_VALUE, NumberByteArrayConverter.decode(NumberByteArrayConverter.encode(Long.MIN_VALUE, 8), 8))
        assertEquals(Long.MAX_VALUE, NumberByteArrayConverter.decode(NumberByteArrayConverter.encode(Long.MAX_VALUE, 8), 8))
        assertEquals(0L, NumberByteArrayConverter.decode(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0), 8))
    }

    @Test
    fun `decode masks each byte so high bytes are read unsigned`() {
        // A single 0x80 byte at index 0 must contribute 0x80 (128), not a
        // sign-extended -128. Pins the per-byte `and 0xFF` masking directly.
        assertEquals(0x80L, NumberByteArrayConverter.decode(byteArrayOf(0x80.toByte()), 1))
        assertEquals(0xFFL, NumberByteArrayConverter.decode(byteArrayOf(0xFF.toByte()), 1))
        // 4-byte 0xFFFFFFFF read as unsigned magnitude 4294967295, not -1.
        assertEquals(0xFFFFFFFFL, NumberByteArrayConverter.decode(byteArrayOf(-1, -1, -1, -1), 4))
    }
}
