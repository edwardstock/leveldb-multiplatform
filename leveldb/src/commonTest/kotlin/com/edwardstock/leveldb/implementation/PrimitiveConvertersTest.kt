package com.edwardstock.leveldb.implementation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrimitiveConvertersTest {
    @Test
    fun `bool converter round trip and null`() {
        val converter = BoolConverter()
        assertEquals(true, converter.decode(byteArrayOf(), converter.encode(true)))
        assertEquals(false, converter.decode(byteArrayOf(), converter.encode(false)))
        assertNull(converter.decode(byteArrayOf(), null))
    }

    @Test
    fun `byte and ubyte converters round trip`() {
        val byteConverter = ByteConverter()
        val ubyteConverter = UByteConverter()
        assertEquals(1.toByte(), byteConverter.decode(byteArrayOf(), byteConverter.encode(1)))
        assertEquals(255.toUByte(), ubyteConverter.decode(byteArrayOf(), ubyteConverter.encode(255.toUByte())))
    }

    @Test
    fun `char and short converters handle round trip and short input`() {
        val charConverter = CharConverter()
        val shortConverter = ShortConverter()
        val ushortConverter = UShortConverter()

        assertEquals('Z', charConverter.decode(byteArrayOf(), charConverter.encode('Z')))
        assertEquals(1234.toShort(), shortConverter.decode(byteArrayOf(), shortConverter.encode(1234)))
        assertEquals(65530.toUShort(), ushortConverter.decode(byteArrayOf(), ushortConverter.encode(65530.toUShort())))

        assertNull(charConverter.decode(byteArrayOf(), byteArrayOf(1)))
        assertNull(shortConverter.decode(byteArrayOf(), byteArrayOf(1)))
        assertNull(ushortConverter.decode(byteArrayOf(), byteArrayOf(1)))
    }

    @Test
    fun `int and uint converters handle round trip and short input`() {
        val intConverter = IntConverter()
        val uintConverter = UIntConverter()

        assertEquals(123456, intConverter.decode(byteArrayOf(), intConverter.encode(123456)))
        assertEquals(4000000000u, uintConverter.decode(byteArrayOf(), uintConverter.encode(4000000000u)))

        assertNull(intConverter.decode(byteArrayOf(), byteArrayOf(1, 2, 3)))
        assertNull(uintConverter.decode(byteArrayOf(), byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `long and ulong converters handle round trip and short input`() {
        val longConverter = LongConverter()
        val ulongConverter = ULongConverter()

        assertEquals(1234567890123L, longConverter.decode(byteArrayOf(), longConverter.encode(1234567890123L)))
        assertEquals(9000000000000000000u, ulongConverter.decode(byteArrayOf(), ulongConverter.encode(9000000000000000000u)))

        assertNull(longConverter.decode(byteArrayOf(), byteArrayOf(1, 2, 3, 4, 5, 6, 7)))
        assertNull(ulongConverter.decode(byteArrayOf(), byteArrayOf(1, 2, 3, 4, 5, 6, 7)))
    }

    @Test
    fun `float converter round trip and short input`() {
        val converter = FloatConverter()
        val value = 123.5f
        val decoded = converter.decode(byteArrayOf(), converter.encode(value))
        assertEquals(value.toRawBits(), decoded?.toRawBits())
        assertNull(converter.decode(byteArrayOf(), byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `double converter round trip and short input`() {
        val converter = DoubleConverter()
        val value = 123456.125
        val decoded = converter.decode(byteArrayOf(), converter.encode(value))
        assertEquals(value.toRawBits(), decoded?.toRawBits())
        assertNull(converter.decode(byteArrayOf(), byteArrayOf(1, 2, 3, 4, 5, 6, 7)))
    }
}
