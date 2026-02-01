package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.exception.LevelDBDecodingException
import com.edwardstock.leveldb.internal.NumberByteArrayConverter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PrimitiveConvertersTest {
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
}
