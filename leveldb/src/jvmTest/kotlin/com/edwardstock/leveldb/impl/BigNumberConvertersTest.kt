package com.edwardstock.leveldb.impl

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun `big integer converter encodes minimal two's complement big-endian`() {
        val converter = BigIntegerConverter()
        // BigInteger.toByteArray() is big-endian, two's complement, minimal length.
        assertContentEquals(byteArrayOf(0x01), converter.encode(BigInteger.ONE))
        assertContentEquals(byteArrayOf(0x00), converter.encode(BigInteger.ZERO))
        assertContentEquals(byteArrayOf(-1), converter.encode(BigInteger.valueOf(-1)))
        // 256 needs a leading zero byte to stay positive: [0x01, 0x00].
        assertContentEquals(byteArrayOf(0x01, 0x00), converter.encode(BigInteger.valueOf(256)))
    }

    @Test
    fun `big decimal converter layout is 4-byte big-endian scale prefix then unscaled bytes`() {
        val converter = BigDecimalConverter()

        // 12345.6789 -> unscaledValue 123456789, scale 4.
        // Layout: [00 00 00 04] ++ BigInteger(123456789).toByteArray() == [07 5B CD 15].
        // A wrong scale width, little-endian scale, or reordered prefix/payload
        // all change these exact bytes; the round-trip test above would not notice.
        assertContentEquals(
            byteArrayOf(0, 0, 0, 4, 0x07, 0x5B, 0xCD.toByte(), 0x15),
            converter.encode(BigDecimal("12345.6789")),
        )

        // scale 0, unscaled 1 -> [00 00 00 00, 01].
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0, 0x01),
            converter.encode(BigDecimal(BigInteger.ONE, 0)),
        )

        // Negative scale (e.g. 1E+2 has scale -2) stored as int32 two's complement,
        // big-endian: scale -2 == [0xFF,0xFF,0xFF,0xFE], unscaled 1 == [0x01].
        assertContentEquals(
            byteArrayOf(-1, -1, -1, -2, 0x01),
            converter.encode(BigDecimal(BigInteger.ONE, -2)),
        )

        // Decoding that exact layout must reconstruct the value, including the
        // negative scale path (decode does .toInt() on the 4 scale bytes).
        assertEquals(
            BigDecimal(BigInteger.ONE, -2),
            converter.decode(byteArrayOf(-1, -1, -1, -2, 0x01)),
        )
    }

    @Test
    fun `big decimal zero keeps a one-byte unscaled payload`() {
        val converter = BigDecimalConverter()

        // BigInteger.ZERO.toByteArray() is [0x00] (one byte), NOT empty.
        // So encode(ZERO) must be [00 00 00 00] (scale 0) ++ [00] == 5 bytes.
        // A mutation that drops the leading unscaled byte for zero
        // (emitting an empty unscaled payload) still round-trip-decodes but
        // corrupts the on-disk shape to a 4-byte value; this pins the 5th byte.
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0, 0x00),
            converter.encode(BigDecimal.ZERO),
        )
        assertEquals(5, converter.encode(BigDecimal.ZERO).size)

        // And it must decode back to zero with scale 0.
        val decoded = converter.decode(byteArrayOf(0, 0, 0, 0, 0x00))
        assertEquals(0, decoded.compareTo(BigDecimal.ZERO))
        assertEquals(0, decoded.scale())
    }

    @Test
    fun `big decimal scale prefix width is exactly 4 bytes`() {
        val converter = BigDecimalConverter()
        // Scale 4 -> prefix [00 00 00 04]. If the scale width were narrowed to 2
        // ([00 04] ++ unscaled) the encoded array shrinks by 2 and the first
        // bytes shift; this isolates the 4-byte scale-prefix width independent
        // of the unscaled payload.
        val encoded = converter.encode(BigDecimal("12345.6789"))
        // 4 scale bytes + 4 unscaled bytes (123456789 == 0x075BCD15).
        assertEquals(8, encoded.size)
        assertContentEquals(byteArrayOf(0, 0, 0, 4), encoded.copyOfRange(0, 4))
    }

    @Test
    fun `big decimal decode rejects an empty unscaled payload`() {
        val converter = BigDecimalConverter()
        // Exactly 4 bytes (scale only, no unscaled payload) is below the minimum
        // the format allows: production requires size >= 4 but BigInteger(empty)
        // throws NumberFormatException, so a 4-byte input is a corrupt record.
        // Pin that a scale-only buffer does not silently decode to a valid value.
        assertFailsWith<Exception> { converter.decode(byteArrayOf(0, 0, 0, 0)) }
    }
}
