/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.implementation

import com.edwardstock.leveldb.ValueAdapter
import com.edwardstock.leveldb.utils.NumberByteArrayConverter

class BoolConverter : ValueAdapter<Boolean> {
    override fun decode(key: ByteArray, value: ByteArray?): Boolean? {
        return value?.let { it.isNotEmpty() && it[0] == 1.toByte() }
    }

    override fun encode(value: Boolean): ByteArray {
        return byteArrayOf(if (value) 1 else 0)
    }
}

class CharConverter : ValueAdapter<Char> {
    override fun decode(key: ByteArray, value: ByteArray?): Char? {
        return value?.let {
            if (it.size >= 2) {
                NumberByteArrayConverter.decode(it, 2).toInt().toChar()
            } else null
        }
    }

    override fun encode(value: Char): ByteArray {
        return NumberByteArrayConverter.encode(value.code.toLong(), 2)
    }
}

class ShortConverter : ValueAdapter<Short> {
    override fun decode(key: ByteArray, value: ByteArray?): Short? {
        return value?.let {
            if (it.size >= 2) {
                NumberByteArrayConverter.decode(it, 2).toShort()
            } else null
        }
    }

    override fun encode(value: Short): ByteArray {
        return NumberByteArrayConverter.encode(value.toLong(), 2)
    }
}

class UShortConverter : ValueAdapter<UShort> {
    override fun decode(key: ByteArray, value: ByteArray?): UShort? {
        return value?.let {
            if (it.size >= 2) {
                NumberByteArrayConverter.decode(it, 2).toUShort()
            } else null
        }
    }

    override fun encode(value: UShort): ByteArray {
        return NumberByteArrayConverter.encode(value.toLong(), 2)
    }
}

class ByteConverter : ValueAdapter<Byte> {
    override fun decode(key: ByteArray, value: ByteArray?): Byte? {
        return value?.firstOrNull()
    }

    override fun encode(value: Byte): ByteArray {
        return byteArrayOf(value)
    }
}

class UByteConverter : ValueAdapter<UByte> {
    override fun decode(key: ByteArray, value: ByteArray?): UByte? {
        return value?.firstOrNull()?.toUByte()
    }

    override fun encode(value: UByte): ByteArray {
        return byteArrayOf(value.toByte())
    }
}

class IntConverter : ValueAdapter<Int> {
    override fun decode(key: ByteArray, value: ByteArray?): Int? {
        return value?.let {
            if (it.size >= 4) {
                NumberByteArrayConverter.decode(it, 4).toInt()
            } else null
        }
    }

    override fun encode(value: Int): ByteArray {
        return NumberByteArrayConverter.encode(value.toLong(), 4)
    }
}

class UIntConverter : ValueAdapter<UInt> {
    override fun decode(key: ByteArray, value: ByteArray?): UInt? {
        return value?.let {
            if (it.size >= 4) {
                NumberByteArrayConverter.decode(it, 4).toUInt()
            } else null
        }
    }

    override fun encode(value: UInt): ByteArray {
        return NumberByteArrayConverter.encode(value.toLong(), 4)
    }
}

class LongConverter : ValueAdapter<Long> {
    override fun decode(key: ByteArray, value: ByteArray?): Long? {
        return value?.let {
            if (it.size >= 8) {
                NumberByteArrayConverter.decode(it, 8)
            } else null
        }
    }

    override fun encode(value: Long): ByteArray {
        return NumberByteArrayConverter.encode(value, 8)
    }
}

class ULongConverter : ValueAdapter<ULong> {
    override fun decode(key: ByteArray, value: ByteArray?): ULong? {
        return value?.let {
            if (it.size >= 8) {
                NumberByteArrayConverter.decode(it, 8).toULong()
            } else null
        }
    }

    override fun encode(value: ULong): ByteArray {
        return NumberByteArrayConverter.encode(value.toLong(), 8)
    }
}

class FloatConverter : ValueAdapter<Float> {
    override fun decode(key: ByteArray, value: ByteArray?): Float? {
        return value?.let {
            if (it.size >= 4) {
                Float.fromBits(NumberByteArrayConverter.decode(it, 4).toInt())
            } else null
        }
    }

    override fun encode(value: Float): ByteArray {
        return NumberByteArrayConverter.encode(value.toRawBits().toLong(), 4)
    }
}

class DoubleConverter : ValueAdapter<Double> {
    override fun decode(key: ByteArray, value: ByteArray?): Double? {
        return value?.let {
            if (it.size >= 8) {
                Double.fromBits(NumberByteArrayConverter.decode(it, 8))
            } else null
        }
    }

    override fun encode(value: Double): ByteArray {
        return NumberByteArrayConverter.encode(value.toRawBits(), 8)
    }
}
