/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.api.ValueAdapter
import com.edwardstock.leveldb.exception.LevelDBDecodingException
import com.edwardstock.leveldb.internal.NumberByteArrayConverter

private fun checkDecodedSize(value: ByteArray, expectedSize: Int, type: String) {
    if (value.size != expectedSize) throw LevelDBDecodingException(
        "Corrupted LevelDB value for ${type}: expected $expectedSize bytes, got ${value.size}"
    )
}

class BoolConverter : ValueAdapter<Boolean> {
    override fun decode(value: ByteArray): Boolean {
        checkDecodedSize(value, 1, "Boolean")
        return value[0] == 1.toByte()
    }

    override fun encode(value: Boolean): ByteArray {
        return byteArrayOf(if (value) 1 else 0)
    }
}

class CharConverter : ValueAdapter<Char> {
    override fun decode(value: ByteArray): Char {
        return value.let {
            checkDecodedSize(it, 2, "Char")
            NumberByteArrayConverter.decode(it, it.size).toInt().toChar()
        }
    }

    override fun encode(value: Char): ByteArray {
        return NumberByteArrayConverter.encode(value.code.toLong(), 2)
    }
}

class ShortConverter : ValueAdapter<Short> {
    override fun decode(value: ByteArray): Short {
        return value.let {
            checkDecodedSize(it, 2, "Short")
            NumberByteArrayConverter.decode(it, 2).toShort()
        }
    }

    override fun encode(value: Short): ByteArray {
        return NumberByteArrayConverter.encode(value.toLong(), 2)
    }
}

class UShortConverter : ValueAdapter<UShort> {
    override fun decode(value: ByteArray): UShort {
        return value.let {
            checkDecodedSize(it, 2, "UShort")
            NumberByteArrayConverter.decode(it, 2).toUShort()
        }
    }

    override fun encode(value: UShort): ByteArray {
        return NumberByteArrayConverter.encode(value.toLong(), 2)
    }
}

class ByteConverter : ValueAdapter<Byte> {
    override fun decode(value: ByteArray): Byte {
        checkDecodedSize(value, 1, "Byte")
        return value.first()
    }

    override fun encode(value: Byte): ByteArray {
        return byteArrayOf(value)
    }
}

class UByteConverter : ValueAdapter<UByte> {
    override fun decode(value: ByteArray): UByte {
        checkDecodedSize(value, 1, "UByte")
        return value.first().toUByte()
    }

    override fun encode(value: UByte): ByteArray {
        return byteArrayOf(value.toByte())
    }
}

class IntConverter : ValueAdapter<Int> {
    override fun decode(value: ByteArray): Int {
        return value.let {
            checkDecodedSize(it, 4, "Int")
            NumberByteArrayConverter.decode(it, 4).toInt()
        }
    }

    override fun encode(value: Int): ByteArray {
        return NumberByteArrayConverter.encode(value.toLong(), 4)
    }
}

class UIntConverter : ValueAdapter<UInt> {
    override fun decode(value: ByteArray): UInt {
        return value.let {
            checkDecodedSize(it, 4, "UInt")
            NumberByteArrayConverter.decode(it, 4).toUInt()
        }
    }

    override fun encode(value: UInt): ByteArray {
        return NumberByteArrayConverter.encode(value.toLong(), 4)
    }
}

class LongConverter : ValueAdapter<Long> {
    override fun decode(value: ByteArray): Long {
        return value.let {
            checkDecodedSize(it, 8, "Long")
            NumberByteArrayConverter.decode(it, 8)
        }
    }

    override fun encode(value: Long): ByteArray {
        return NumberByteArrayConverter.encode(value, 8)
    }
}

class ULongConverter : ValueAdapter<ULong> {
    override fun decode(value: ByteArray): ULong {
        return value.let {
            checkDecodedSize(it, 8, "ULong")
            NumberByteArrayConverter.decode(it, 8).toULong()
        }
    }

    override fun encode(value: ULong): ByteArray {
        return NumberByteArrayConverter.encode(value.toLong(), 8)
    }
}

class FloatConverter : ValueAdapter<Float> {
    override fun decode(value: ByteArray): Float {
        return value.let {
            checkDecodedSize(it, 4, "Float")
            Float.fromBits(NumberByteArrayConverter.decode(it, 4).toInt())
        }
    }

    override fun encode(value: Float): ByteArray {
        return NumberByteArrayConverter.encode(value.toRawBits().toLong(), 4)
    }
}

class DoubleConverter : ValueAdapter<Double> {
    override fun decode(value: ByteArray): Double {
        return value.let {
            checkDecodedSize(it, 8, "Double")
            Double.fromBits(NumberByteArrayConverter.decode(it, 8))
        }
    }

    override fun encode(value: Double): ByteArray {
        return NumberByteArrayConverter.encode(value.toRawBits(), 8)
    }
}
