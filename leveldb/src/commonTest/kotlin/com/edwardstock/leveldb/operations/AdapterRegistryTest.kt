package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.AdapterRegistry
import com.edwardstock.leveldb.api.ValueAdapter
import com.edwardstock.leveldb.exception.LevelDBNoTypeAdapterException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class AdapterRegistryTest {

    /**
     * A custom Int adapter with sentinel encode/decode behavior that is distinct
     * from the built-in IntConverter, so we can prove which adapter is actually used.
     */
    private class SentinelIntAdapter(
        private val encodeBytes: ByteArray = byteArrayOf(7, 7, 7),
        private val decodeValue: Int = -999,
    ) : ValueAdapter<Int> {
        override fun decode(value: ByteArray): Int = decodeValue
        override fun encode(value: Int): ByteArray = encodeBytes
    }

    private class StringAdapter : ValueAdapter<String> {
        override fun decode(value: ByteArray): String = "decoded"
        override fun encode(value: String): ByteArray = byteArrayOf(9)
    }

    @Test
    fun `decode with no adapter throws LevelDBNoTypeAdapterException`() {
        // Start from a registry with the default adapters, then ensure String has none.
        val registry = AdapterRegistry()

        // String is not among the default adapters and has not been added.
        assertNull(registry.findAdapter(String::class))

        // Catches: decode() silently returning instead of throwing when no adapter exists.
        assertFailsWith<LevelDBNoTypeAdapterException> {
            registry.decode(byteArrayOf(1, 2, 3), String::class)
        }
    }

    @Test
    fun `encode with no adapter throws LevelDBNoTypeAdapterException`() {
        val registry = AdapterRegistry()
        assertNull(registry.findAdapter(String::class))

        // Catches: encode() silently returning instead of throwing when no adapter exists.
        assertFailsWith<LevelDBNoTypeAdapterException> {
            registry.encode("anything", String::class)
        }
    }

    @Test
    fun `default registry resolves built-in Int adapter`() {
        val registry = AdapterRegistry()

        // Catches: default adapters not being installed by the no-arg constructor.
        assertNotNull(registry.findAdapter(Int::class))
    }

    @Test
    fun `custom adapter overrides the default for the same type`() {
        val registry = AdapterRegistry()
        // Sanity: the default Int adapter does NOT produce our sentinel bytes.
        val defaultEncoded = registry.encode(123, Int::class)
        assertNotEqualsContent(byteArrayOf(7, 7, 7), defaultEncoded)

        val custom = SentinelIntAdapter()
        registry.addAdapter(Int::class, custom)

        // After override, lookup returns exactly the custom instance.
        // Catches: addAdapter() not replacing an existing mapping for the same KClass.
        assertSame(custom, registry.findAdapter(Int::class))

        // And encode/decode now route through the custom adapter, not the default.
        assertContentEquals(byteArrayOf(7, 7, 7), registry.encode(123, Int::class))
        assertEquals(-999, registry.decode(byteArrayOf(0), Int::class))
    }

    @Test
    fun `removeAdapter drops only the targeted adapter`() {
        val registry = AdapterRegistry()
        val custom = StringAdapter()
        registry.addAdapter(String::class, custom)
        assertNotNull(registry.findAdapter(String::class))
        // A different default type remains present alongside the custom one.
        assertNotNull(registry.findAdapter(Int::class))

        registry.removeAdapter(String::class)

        // Catches: removeAdapter() being a no-op (left mapping in place).
        assertNull(registry.findAdapter(String::class))
        assertFailsWith<LevelDBNoTypeAdapterException> {
            registry.encode("x", String::class)
        }
        // Catches: removeAdapter() clearing too much (touching other types).
        assertNotNull(registry.findAdapter(Int::class))
    }

    @Test
    fun `clearAdapters removes every adapter`() {
        val registry = AdapterRegistry()
        registry.addAdapter(String::class, StringAdapter())
        // Pre-condition: at least Int (default) and String (added) resolve.
        assertNotNull(registry.findAdapter(Int::class))
        assertNotNull(registry.findAdapter(String::class))

        registry.clearAdapters()

        // Catches: clearAdapters() being a no-op or leaving defaults behind.
        assertNull(registry.findAdapter(Int::class))
        assertNull(registry.findAdapter(String::class))
        assertFailsWith<LevelDBNoTypeAdapterException> {
            registry.encode(1, Int::class)
        }
    }

    @Test
    fun `copy mutating the copy does not affect the original`() {
        val original = AdapterRegistry()
        val copy = original.copy()

        val custom = SentinelIntAdapter()
        // Mutate the copy in several ways.
        copy.addAdapter(Int::class, custom)        // override existing default
        copy.addAdapter(String::class, StringAdapter()) // add a new type
        copy.removeAdapter(Double::class)          // remove a default from the copy

        // Original Int adapter is untouched: it must still be the default, not the sentinel.
        // The default encoding must differ from the sentinel bytes.
        // Catches: copy() sharing the backing map (override leaking back).
        assertNotNull(original.findAdapter(Int::class))
        assertNotEqualsContent(byteArrayOf(7, 7, 7), original.encode(123, Int::class))

        // String added only to the copy must not appear in the original.
        // Catches: copy() sharing the backing map (additions leaking back).
        assertNull(original.findAdapter(String::class))

        // Double removed only from the copy must still resolve in the original.
        // Catches: copy() sharing the backing map (removals leaking back).
        assertNotNull(original.findAdapter(Double::class))
    }

    @Test
    fun `copy mutating the original does not affect the copy`() {
        val original = AdapterRegistry()
        val copy = original.copy()

        original.clearAdapters()

        // The copy snapshot must retain the defaults despite the original being cleared.
        // Catches: copy() sharing the backing map (clear on original wiping the copy).
        assertNotNull(copy.findAdapter(Int::class))
        assertNotNull(copy.findAdapter(Double::class))
    }

    private fun assertNotEqualsContent(unexpected: ByteArray, actual: ByteArray) {
        val same = unexpected.size == actual.size &&
            unexpected.indices.all { unexpected[it] == actual[it] }
        kotlin.test.assertFalse(
            same,
            "Expected byte arrays to differ, but both were ${actual.toList()}",
        )
    }
}
