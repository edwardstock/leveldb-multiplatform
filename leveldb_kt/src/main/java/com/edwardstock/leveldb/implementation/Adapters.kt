package com.edwardstock.leveldb.implementation

import com.edwardstock.leveldb.ValueAdapter
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.experimental.and

class BoolConverter : ValueAdapter<Boolean> {
    override fun decode(key: ByteArray, value: ByteArray?): Boolean? {
        return value?.let { it[0] == 1.toByte() }
    }

    override fun encode(value: Boolean): ByteArray {
        return if (value) byteArrayOf(1) else byteArrayOf(0)
    }
}

class CharConverter : ValueAdapter<Char> {
    override fun decode(key: ByteArray, value: ByteArray?): Char? {
        return value?.let { it[0].toInt().toChar() }
    }

    override fun encode(value: Char): ByteArray {
        return byteArrayOf(value.code.toByte())
    }
}

class ShortConverter : ValueAdapter<Short> {
    override fun decode(key: ByteArray, value: ByteArray?): Short? {
        return value?.let {
            BigInteger(it).toShort()
        }
    }

    override fun encode(value: Short): ByteArray {
        return BigInteger.valueOf(value.toLong()).toByteArray()
    }
}

class UShortConverter : ValueAdapter<UShort> {
    override fun decode(key: ByteArray, value: ByteArray?): UShort? {
        return value?.let {
            BigInteger(it).toShort().toUShort()
        }
    }

    override fun encode(value: UShort): ByteArray {
        return BigInteger.valueOf(value.toLong() and 0xFFFF).toByteArray()
    }
}

class ByteConverter : ValueAdapter<Byte> {
    override fun decode(key: ByteArray, value: ByteArray?): Byte? {
        return value?.let {
            it[0]
        }
    }

    override fun encode(value: Byte): ByteArray {
        return byteArrayOf(value)
    }
}

class UByteConverter : ValueAdapter<UByte> {
    override fun decode(key: ByteArray, value: ByteArray?): UByte? {
        return value?.let {
            it[0] and 0xFF.toByte()
        }?.toUByte()
    }

    override fun encode(value: UByte): ByteArray {
        return byteArrayOf(value.toByte() and 0xFF.toByte())
    }
}

class IntConverter : ValueAdapter<Int> {
    override fun decode(key: ByteArray, value: ByteArray?): Int? {
        return value?.let {
            BigInteger(it).toInt()
        }
    }

    override fun encode(value: Int): ByteArray {
        return BigInteger.valueOf(value.toLong()).toByteArray()
    }
}

class UIntConverter : ValueAdapter<UInt> {
    override fun decode(key: ByteArray, value: ByteArray?): UInt? {
        return value?.let {
            BigInteger(it).toInt().toUInt()
        }
    }

    override fun encode(value: UInt): ByteArray {
        return BigInteger.valueOf(value.toLong() and 0xFFFFFFFF).toByteArray()
    }
}

class LongConverter : ValueAdapter<Long> {
    override fun decode(key: ByteArray, value: ByteArray?): Long? {
        return value?.let {
            BigInteger(value).toLong()
        }
    }

    override fun encode(value: Long): ByteArray {
        return BigInteger.valueOf(value).toByteArray()
    }
}

class ULongConverter : ValueAdapter<ULong> {
    override fun decode(key: ByteArray, value: ByteArray?): ULong? {
        return value?.let {
            BigInteger(value).toLong().toULong()
        }
    }

    override fun encode(value: ULong): ByteArray {
        return BigInteger.valueOf(value.toLong()).toByteArray()
    }
}

class FloatConverter : ValueAdapter<Float> {
    override fun decode(key: ByteArray, value: ByteArray?): Float? {
        return value?.toString()?.toFloatOrNull()
    }

    override fun encode(value: Float): ByteArray {
        return value.toString().toByteArray()
    }
}

class DoubleConverter : ValueAdapter<Double> {
    override fun decode(key: ByteArray, value: ByteArray?): Double? {
        return value?.toString()?.toDoubleOrNull()
    }

    override fun encode(value: Double): ByteArray {
        return value.toString().toByteArray()
    }
}

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
        return value?.toString()?.let { BigDecimal(it) }
    }

    override fun encode(value: BigDecimal): ByteArray {
        return value.toEngineeringString().toByteArray()
    }
}

