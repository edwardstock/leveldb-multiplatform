package com.edwardstock.leveldb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LevelDBConfigTest {
    private class IntStringAdapter : ValueAdapter<Int> {
        var lastEncoded: ByteArray? = null

        override fun decode(key: ByteArray, value: ByteArray?): Int? {
            return value?.decodeToString()?.toInt()
        }

        override fun encode(value: Int): ByteArray {
            return value.toString().encodeToByteArray().also { lastEncoded = it }
        }
    }

    @Test
    fun `adapters can be registered and resolved`() {
        val adapter = IntStringAdapter()
        val config = LevelDBConfig(adapters = mutableMapOf())

        config.addAdapter(adapter)
        assertSame(adapter, config.findAdapter<Int>())
        assertSame(adapter, config.findAdapter(Int::class))
    }

    @Test
    fun `convert helpers use adapter encoding`() {
        val adapter = IntStringAdapter()
        val config = LevelDBConfig(adapters = mutableMapOf(Int::class to adapter))

        val value = config.convertT<Int>("k", "123")
        assertEquals(123, value)

        val result = config.convertFromT(456)
        assertEquals(adapter.lastEncoded?.toString(), result)
    }
}
