package com.edwardstock.leveldb

import com.edwardstock.leveldb.api.ValueAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LevelDBInstanceConfigTest {
    private class IntStringAdapter : ValueAdapter<Int> {
        var lastEncoded: ByteArray? = null

        override fun decode(value: ByteArray): Int {
            return value.decodeToString().toInt()
        }

        override fun encode(value: Int): ByteArray {
            return value.toString().encodeToByteArray().also { lastEncoded = it }
        }
    }

    @Test
    fun `adapters can be registered and resolved`() {
        val adapter = IntStringAdapter()
        val config = AdapterRegistry(mutableMapOf())

        config.addAdapter(adapter)
        assertSame(adapter, config.findAdapter<Int>())
        assertSame(adapter, config.findAdapter(Int::class))
    }

    @Test
    fun `convert helpers use adapter encoding`() {
        val adapter = IntStringAdapter()
        val config = AdapterRegistry(mutableMapOf(Int::class to adapter))

        val value = config.decode<Int>(123.toString().encodeToByteArray())
        assertEquals(123, value)

        // "456"
        val result = config.encode(456).decodeToString()
        assertEquals(adapter.lastEncoded?.decodeToString(), result)
    }
}
