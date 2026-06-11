package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.exception.LevelDBDecodingException
import com.edwardstock.leveldb.internal.NumberByteArrayConverter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PrimitiveConvertersTest {

    // --- On-disk byte layout (big-endian). These pin the exact wire format so
    // a switch to little-endian, a wrong width, or wrong raw-bits handling is
    // caught immediately instead of slipping past a decode(encode(x)) round trip. ---

    @Test
    fun `int converter encodes big-endian fixed 4 bytes`() {
        val converter = IntConverter()
        // The headline format guarantee from the spec: encode(1) == [0,0,0,1].
        assertContentEquals(byteArrayOf(0, 0, 0, 1), converter.encode(1))
        // Distinct bytes pin order; little-endian would give [0x04,0x03,0x02,0x01].
        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), converter.encode(0x01020304))
        // Negative: two's complement, sign byte first.
        assertContentEquals(byteArrayOf(-1, -1, -1, -1), converter.encode(-1))
        assertContentEquals(byteArrayOf(0x80.toByte(), 0, 0, 0), converter.encode(Int.MIN_VALUE))
    }

    @Test
    fun `uint converter encodes big-endian fixed 4 bytes`() {
        val converter = UIntConverter()
        assertContentEquals(byteArrayOf(0, 0, 0, 1), converter.encode(1u))
        // 4_000_000_000 = 0xEE6B2800; high bit set proves unsigned high byte at index 0.
        assertContentEquals(
            byteArrayOf(0xEE.toByte(), 0x6B, 0x28, 0x00),
            converter.encode(4_000_000_000u),
        )
    }

    @Test
    fun `long converter encodes big-endian fixed 8 bytes`() {
        val converter = LongConverter()
        assertContentEquals(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1), converter.encode(1L))
        assertContentEquals(
            byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x78),
            converter.encode(0x1122334455667778L),
        )
        assertContentEquals(byteArrayOf(-1, -1, -1, -1, -1, -1, -1, -1), converter.encode(-1L))

        // Sign bit isolated in the MSB at index 0 (the 8-byte analogue of
        // Int.MIN_VALUE). Long.MIN_VALUE == 0x8000000000000000.
        // Catches a mutation that mishandles the top byte / sign extension at
        // the extreme: -1L (all-FF) does NOT isolate the MSB, this does.
        assertContentEquals(
            byteArrayOf(0x80.toByte(), 0, 0, 0, 0, 0, 0, 0),
            converter.encode(Long.MIN_VALUE),
        )
        // Long.MAX_VALUE == 0x7FFFFFFFFFFFFFFF: top byte 0x7F, rest 0xFF.
        assertContentEquals(
            byteArrayOf(0x7F, -1, -1, -1, -1, -1, -1, -1),
            converter.encode(Long.MAX_VALUE),
        )
    }

    @Test
    fun `ulong converter encodes high-bit-set extreme big-endian`() {
        val converter = ULongConverter()
        // 9223372036854775808 == 0x8000000000000000: only the top bit set.
        // Pins the unsigned high byte at index 0 (round-trip 9e18 does not
        // isolate the MSB the way this does).
        assertContentEquals(
            byteArrayOf(0x80.toByte(), 0, 0, 0, 0, 0, 0, 0),
            converter.encode(9223372036854775808u),
        )
        // ULong.MAX_VALUE == 0xFFFFFFFFFFFFFFFF.
        assertContentEquals(
            byteArrayOf(-1, -1, -1, -1, -1, -1, -1, -1),
            converter.encode(ULong.MAX_VALUE),
        )
    }

    @Test
    fun `short and char encode big-endian fixed 2 bytes`() {
        assertContentEquals(byteArrayOf(0x01, 0x02), ShortConverter().encode(0x0102))
        assertContentEquals(byteArrayOf(0, 1), ShortConverter().encode(1))
        // 'Z' == 0x005A
        assertContentEquals(byteArrayOf(0x00, 0x5A), CharConverter().encode('Z'))
    }

    @Test
    fun `bool and byte encode single byte layout`() {
        assertContentEquals(byteArrayOf(1), BoolConverter().encode(true))
        assertContentEquals(byteArrayOf(0), BoolConverter().encode(false))
        assertContentEquals(byteArrayOf(0x7F), ByteConverter().encode(0x7F))
        assertContentEquals(byteArrayOf(0xFF.toByte()), UByteConverter().encode(255u))
    }

    @Test
    fun `float converter encodes IEEE754 raw bits big-endian`() {
        val converter = FloatConverter()
        // 1.0f == 0x3F800000. A little-endian write would be [0,0,0x80,0x3F].
        assertContentEquals(byteArrayOf(0x3F, 0x80.toByte(), 0, 0), converter.encode(1.0f))
        // 123.5f == 0x42F70000 (matches the round-trip case below).
        assertContentEquals(byteArrayOf(0x42, 0xF7.toByte(), 0, 0), converter.encode(123.5f))
        // -1.5f == 0xBFC00000: sign bit in the top byte at index 0.
        assertContentEquals(byteArrayOf(0xBF.toByte(), 0xC0.toByte(), 0, 0), converter.encode(-1.5f))
    }

    @Test
    fun `float converter preserves exact NaN Inf and negative zero raw bits`() {
        val converter = FloatConverter()
        // Canonical NaN == 0x7FC00000.
        assertContentEquals(byteArrayOf(0x7F, 0xC0.toByte(), 0, 0), converter.encode(Float.NaN))
        // NON-canonical NaN payload (0x7FC00001) is the assertion that actually
        // pins the toRawBits() choice: toRawBits() preserves the exact payload,
        // whereas toBits() collapses EVERY NaN to 0x7FC00000. A
        // toRawBits() -> toBits() mutation changes the last byte 0x01 -> 0x00
        // and fails here; the canonical-NaN case above would NOT catch it.
        assertContentEquals(
            byteArrayOf(0x7F, 0xC0.toByte(), 0x00, 0x01),
            converter.encode(Float.fromBits(0x7FC00001)),
        )
        // +Inf == 0x7F800000.
        assertContentEquals(byteArrayOf(0x7F, 0x80.toByte(), 0, 0), converter.encode(Float.POSITIVE_INFINITY))
        // -Inf == 0xFF800000: sign bit + exponent all-ones.
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0, 0), converter.encode(Float.NEGATIVE_INFINITY))
        // -0.0f == 0x80000000, DISTINCT from +0.0f == 0x00000000. Any zero
        // normalization regression on encode would silently merge negative zero
        // into positive zero on disk; this catches it.
        assertContentEquals(byteArrayOf(0x80.toByte(), 0, 0, 0), converter.encode(-0.0f))
        assertContentEquals(byteArrayOf(0, 0, 0, 0), converter.encode(0.0f))
    }

    @Test
    fun `float converter decode preserves NaN and negative zero bits`() {
        val converter = FloatConverter()
        // decode must NOT canonicalize. Reading the -0.0 layout back must yield
        // -0.0 (raw bits 0x80000000), not +0.0. Float.fromBits preserves this;
        // a mutation that normalizes on decode would lose the sign of zero.
        assertEquals(0x80000000.toInt(), converter.decode(byteArrayOf(0x80.toByte(), 0, 0, 0)).toRawBits())
        // NaN layout round-trips with its exact bits intact.
        assertEquals(0x7FC00000, converter.decode(byteArrayOf(0x7F, 0xC0.toByte(), 0, 0)).toRawBits())
    }

    @Test
    fun `double converter encodes IEEE754 raw bits big-endian`() {
        val converter = DoubleConverter()
        // 1.0 == 0x3FF0000000000000.
        assertContentEquals(
            byteArrayOf(0x3F, 0xF0.toByte(), 0, 0, 0, 0, 0, 0),
            converter.encode(1.0),
        )
        // 123456.125 == 0x40FE240200000000 (matches the round-trip case below).
        assertContentEquals(
            byteArrayOf(0x40, 0xFE.toByte(), 0x24, 0x02, 0, 0, 0, 0),
            converter.encode(123456.125),
        )
        // -2.0 == 0xC000000000000000: sign bit in the top byte.
        assertContentEquals(
            byteArrayOf(0xC0.toByte(), 0, 0, 0, 0, 0, 0, 0),
            converter.encode(-2.0),
        )
    }

    @Test
    fun `double converter preserves exact NaN Inf and negative zero raw bits`() {
        val converter = DoubleConverter()
        // Double.NaN canonical raw bits == 0x7FF8000000000000.
        assertContentEquals(
            byteArrayOf(0x7F, 0xF8.toByte(), 0, 0, 0, 0, 0, 0),
            converter.encode(Double.NaN),
        )
        // NON-canonical NaN payload pins toRawBits(): toBits() would collapse
        // this to 0x7FF8000000000000, changing the last byte 0x01 -> 0x00.
        assertContentEquals(
            byteArrayOf(0x7F, 0xF8.toByte(), 0, 0, 0, 0, 0, 0x01),
            converter.encode(Double.fromBits(0x7FF8000000000001L)),
        )
        // +Inf == 0x7FF0000000000000.
        assertContentEquals(
            byteArrayOf(0x7F, 0xF0.toByte(), 0, 0, 0, 0, 0, 0),
            converter.encode(Double.POSITIVE_INFINITY),
        )
        // -Inf == 0xFFF0000000000000.
        assertContentEquals(
            byteArrayOf(0xFF.toByte(), 0xF0.toByte(), 0, 0, 0, 0, 0, 0),
            converter.encode(Double.NEGATIVE_INFINITY),
        )
        // -0.0 == 0x8000000000000000, DISTINCT from +0.0 == 0x0.. . Any zero
        // normalization regression on encode would merge negative zero into
        // positive zero on disk; this catches it.
        assertContentEquals(
            byteArrayOf(0x80.toByte(), 0, 0, 0, 0, 0, 0, 0),
            converter.encode(-0.0),
        )
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            converter.encode(0.0),
        )
    }

    @Test
    fun `double converter decode preserves NaN and negative zero bits`() {
        val converter = DoubleConverter()
        // decode must not canonicalize -0.0 to +0.0 nor alter NaN bits.
        // -0.0 raw bits == 0x8000000000000000 (== Long.MIN_VALUE), distinct
        // from +0.0 (0L). Written as a literal to avoid the unary-minus /
        // .toRawBits() precedence trap.
        assertEquals(
            Long.MIN_VALUE,
            converter.decode(byteArrayOf(0x80.toByte(), 0, 0, 0, 0, 0, 0, 0)).toRawBits(),
        )
        assertEquals(
            0x7FF8000000000000L,
            converter.decode(byteArrayOf(0x7F, 0xF8.toByte(), 0, 0, 0, 0, 0, 0)).toRawBits(),
        )
    }

    @Test
    fun `converters decode big-endian fixed-width layout`() {
        // Pins each converter's own decode wiring (correct width arg + endianness)
        // directly, not transitively via a round trip. A wrong width passed to
        // NumberByteArrayConverter.decode, or a little-endian read, changes these.
        assertEquals(1, IntConverter().decode(byteArrayOf(0, 0, 0, 1)))
        assertEquals(0x01020304, IntConverter().decode(byteArrayOf(0x01, 0x02, 0x03, 0x04)))
        assertEquals(-1, IntConverter().decode(byteArrayOf(-1, -1, -1, -1)))
        assertEquals(Int.MIN_VALUE, IntConverter().decode(byteArrayOf(0x80.toByte(), 0, 0, 0)))

        assertEquals(1u, UIntConverter().decode(byteArrayOf(0, 0, 0, 1)))
        assertEquals(4_000_000_000u, UIntConverter().decode(byteArrayOf(0xEE.toByte(), 0x6B, 0x28, 0x00)))

        assertEquals(1L, LongConverter().decode(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1)))
        assertEquals(Long.MIN_VALUE, LongConverter().decode(byteArrayOf(0x80.toByte(), 0, 0, 0, 0, 0, 0, 0)))
        assertEquals(Long.MAX_VALUE, LongConverter().decode(byteArrayOf(0x7F, -1, -1, -1, -1, -1, -1, -1)))

        assertEquals(
            9223372036854775808u,
            ULongConverter().decode(byteArrayOf(0x80.toByte(), 0, 0, 0, 0, 0, 0, 0)),
        )

        assertEquals(0x0102.toShort(), ShortConverter().decode(byteArrayOf(0x01, 0x02)))
        assertEquals('Z', CharConverter().decode(byteArrayOf(0x00, 0x5A)))
    }

    @Test
    fun `bool converter round trip and null`() {
        val converter = BoolConverter()
        assertEquals(true, converter.decode(converter.encode(true)))
        assertEquals(false, converter.decode(converter.encode(false)))
    }

    @Test
    fun `byte and ubyte converters round trip`() {
        val byteConverter = ByteConverter()
        val ubyteConverter = UByteConverter()
        assertEquals(1.toByte(), byteConverter.decode(byteConverter.encode(1)))
        assertEquals(255.toUByte(), ubyteConverter.decode(ubyteConverter.encode(255.toUByte())))
    }

    @Test
    fun `char and short converters handle round trip and short input`() {
        val charConverter = CharConverter()
        val shortConverter = ShortConverter()
        val ushortConverter = UShortConverter()

        assertEquals('Z', charConverter.decode(charConverter.encode('Z')))
        assertEquals(1234.toShort(), shortConverter.decode(shortConverter.encode(1234)))
        assertEquals(65530.toUShort(), ushortConverter.decode(ushortConverter.encode(65530.toUShort())))

        // must be 2 bytes always
        assertFailsWith<LevelDBDecodingException> { charConverter.decode(byteArrayOf(1)) }
        assertFailsWith<LevelDBDecodingException> { charConverter.decode(NumberByteArrayConverter.encode(0xDEADBEEFL, 8)) }

        // must be 2 bytes always
        assertFailsWith<LevelDBDecodingException> { shortConverter.decode(byteArrayOf(1)) }
        assertFailsWith<LevelDBDecodingException> { shortConverter.decode(NumberByteArrayConverter.encode(0xDEADBEEFL, 8)) }

        // must be 2 bytes always
        assertFailsWith<LevelDBDecodingException> { ushortConverter.decode(byteArrayOf(1)) }
        assertFailsWith<LevelDBDecodingException> { ushortConverter.decode(NumberByteArrayConverter.encode(0xDEADBEEFL, 8)) }
    }

    @Test
    fun `int and uint converters handle round trip and short input`() {
        val intConverter = IntConverter()
        val uintConverter = UIntConverter()

        assertEquals(123456, intConverter.decode(intConverter.encode(123456)))
        assertEquals(4000000000u, uintConverter.decode(uintConverter.encode(4000000000u)))

        assertFailsWith<LevelDBDecodingException> { intConverter.decode(byteArrayOf(1, 2, 3)) }
        assertFailsWith<LevelDBDecodingException> { uintConverter.decode(byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun `long and ulong converters handle round trip and short input`() {
        val longConverter = LongConverter()
        val ulongConverter = ULongConverter()

        assertEquals(1234567890123L, longConverter.decode(longConverter.encode(1234567890123L)))
        assertEquals(9000000000000000000u, ulongConverter.decode(ulongConverter.encode(9000000000000000000u)))

        assertFailsWith<LevelDBDecodingException> { longConverter.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7)) }
        assertFailsWith<LevelDBDecodingException> { ulongConverter.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7)) }
    }

    @Test
    fun `float converter round trip and short input`() {
        val converter = FloatConverter()
        val value = 123.5f
        val decoded = converter.decode(converter.encode(value))
        assertEquals(value.toRawBits(), decoded.toRawBits())
        assertFailsWith<LevelDBDecodingException> { converter.decode(byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun `double converter round trip and short input`() {
        val converter = DoubleConverter()
        val value = 123456.125
        val decoded = converter.decode(converter.encode(value))
        assertEquals(value.toRawBits(), decoded.toRawBits())
        assertFailsWith<LevelDBDecodingException> { converter.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7)) }
    }

    // --- T10 boundary-value gaps: MAX extremes, zero, BoolConverter.decode of a
    // byte != 0/1, and decode-side over-length corruption guards. ---

    @Test
    fun `bool converter decodes any non-one byte as false`() {
        val converter = BoolConverter()
        // The contract is value[0] == 1.toByte(): ONLY 0x01 means true.
        // Pins the exact decode rule so a mutation to e.g. `value[0] != 0`
        // (treat every non-zero byte as true) is caught: 0x02, 0xFF, etc.
        assertEquals(true, converter.decode(byteArrayOf(1)))
        assertEquals(false, converter.decode(byteArrayOf(0)))
        assertEquals(false, converter.decode(byteArrayOf(2)))
        assertEquals(false, converter.decode(byteArrayOf(0x7F)))
        // 0xFF == -1 as a signed Byte; must NOT be read as true.
        assertEquals(false, converter.decode(byteArrayOf(0xFF.toByte())))
        // Length guard still applies: a 2-byte payload is corrupt for Boolean.
        assertFailsWith<LevelDBDecodingException> { converter.decode(byteArrayOf(1, 0)) }
        assertFailsWith<LevelDBDecodingException> { converter.decode(byteArrayOf()) }
    }

    @Test
    fun `int and uint converters pin MAX and zero extremes`() {
        val intConverter = IntConverter()
        val uintConverter = UIntConverter()

        // Int.MAX_VALUE == 0x7FFFFFFF: top byte 0x7F (sign bit clear), rest 0xFF.
        // The existing suite only pins Int.MIN_VALUE; this pins the opposite
        // extreme so a high-byte mask/shift error that survives MIN is caught.
        assertContentEquals(byteArrayOf(0x7F, -1, -1, -1), intConverter.encode(Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, intConverter.decode(byteArrayOf(0x7F, -1, -1, -1)))

        // Zero is its own boundary: all-zero bytes both ways.
        assertContentEquals(byteArrayOf(0, 0, 0, 0), intConverter.encode(0))
        assertEquals(0, intConverter.decode(byteArrayOf(0, 0, 0, 0)))

        // UInt.MAX_VALUE == 0xFFFFFFFF (4294967295). Pins unsigned decode of the
        // all-FF layout to UInt.MAX, not -1: catches a signed widening regression.
        assertContentEquals(byteArrayOf(-1, -1, -1, -1), uintConverter.encode(UInt.MAX_VALUE))
        assertEquals(UInt.MAX_VALUE, uintConverter.decode(byteArrayOf(-1, -1, -1, -1)))
        assertEquals(0u, uintConverter.decode(byteArrayOf(0, 0, 0, 0)))
    }

    @Test
    fun `long and ulong converters pin MAX and zero extremes on decode`() {
        val longConverter = LongConverter()
        val ulongConverter = ULongConverter()

        // Long.MAX_VALUE decode (encode already pinned above). Distinct from
        // -1L: top byte 0x7F isolates correct sign-bit-clear handling.
        assertEquals(Long.MAX_VALUE, longConverter.decode(byteArrayOf(0x7F, -1, -1, -1, -1, -1, -1, -1)))
        assertEquals(0L, longConverter.decode(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)))

        // ULong.MAX_VALUE == 0xFFFFFFFFFFFFFFFF. The existing suite pins ENCODE
        // of ULong.MAX but never DECODE; this pins that the all-FF layout reads
        // back as ULong.MAX (18446744073709551615), not -1L reinterpreted wrong.
        assertEquals(ULong.MAX_VALUE, ulongConverter.decode(byteArrayOf(-1, -1, -1, -1, -1, -1, -1, -1)))
        assertEquals(0uL, ulongConverter.decode(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun `ushort and char converters pin high-bit and MAX boundaries`() {
        val ushortConverter = UShortConverter()
        val charConverter = CharConverter()

        // UShort.MAX_VALUE == 0xFFFF (65535). All-FF must read unsigned, not -1.
        assertContentEquals(byteArrayOf(-1, -1), ushortConverter.encode(UShort.MAX_VALUE))
        assertEquals(UShort.MAX_VALUE, ushortConverter.decode(byteArrayOf(-1, -1)))
        assertEquals(0u.toUShort(), ushortConverter.decode(byteArrayOf(0, 0)))

        // Char.MAX_VALUE == Char(0xFFFF): high bit set in the MSB.
        // Pins big-endian unsigned 2-byte char read at the boundary.
        assertContentEquals(byteArrayOf(-1, -1), charConverter.encode(Char(0xFFFF)))
        assertEquals(Char(0xFFFF), charConverter.decode(byteArrayOf(-1, -1)))
    }

    @Test
    fun `over-length payloads trip the decode corruption guard`() {
        // The length guard rejects BOTH short and long inputs (== expectedSize).
        // The existing suite only exercises short inputs; a mutation that
        // changed `!=` to `<` (accept extra trailing bytes) would pass every
        // existing case but fail here.
        assertFailsWith<LevelDBDecodingException> { IntConverter().decode(byteArrayOf(0, 0, 0, 0, 0)) }
        assertFailsWith<LevelDBDecodingException> { LongConverter().decode(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0)) }
        assertFailsWith<LevelDBDecodingException> { ShortConverter().decode(byteArrayOf(0, 0, 0)) }
        assertFailsWith<LevelDBDecodingException> { ByteConverter().decode(byteArrayOf(0, 0)) }
        assertFailsWith<LevelDBDecodingException> { FloatConverter().decode(byteArrayOf(0, 0, 0, 0, 0)) }
        assertFailsWith<LevelDBDecodingException> { DoubleConverter().decode(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0)) }
    }

    @Test
    fun `float and double decode preserve non-canonical NaN payload`() {
        // Decode-side mirror of the encode tests: Float.fromBits / Double.fromBits
        // must preserve the EXACT payload bits read off disk. A mutation to a
        // canonicalizing read (e.g. via a normalized constructor) would collapse
        // the 0x..01 payload to the canonical NaN and fail here.
        val floatBits = FloatConverter().decode(byteArrayOf(0x7F, 0xC0.toByte(), 0x00, 0x01)).toRawBits()
        assertEquals(0x7FC00001, floatBits)

        val doubleBits = DoubleConverter()
            .decode(byteArrayOf(0x7F, 0xF8.toByte(), 0, 0, 0, 0, 0, 0x01))
            .toRawBits()
        assertEquals(0x7FF8000000000001L, doubleBits)
    }
}
