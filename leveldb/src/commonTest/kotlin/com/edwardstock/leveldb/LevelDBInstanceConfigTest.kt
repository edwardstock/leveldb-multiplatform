package com.edwardstock.leveldb

import com.edwardstock.leveldb.api.LevelDBInstanceFactory
import com.edwardstock.leveldb.api.ValueAdapter
import com.edwardstock.leveldb.config.LevelDBDriverConfig
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.config.toBuilder
import com.edwardstock.leveldb.log.LevelDBConsoleLogger
import com.edwardstock.leveldb.log.LevelDBNullLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    // A user-defined type that the default adapter registry never seeds, so its
    // presence in a registry is an unambiguous signal that *our* mutation landed.
    private data class Custom(val value: Int)

    private class CustomAdapter : ValueAdapter<Custom> {
        override fun decode(value: ByteArray): Custom = Custom(value.decodeToString().toInt())
        override fun encode(value: Custom): ByteArray = value.value.toString().encodeToByteArray()
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

    // ---- builder: driver option propagation ----

    /**
     * Pins that every driver knob set through the builder DSL lands on the built
     * config's [LevelDBDriverConfig]. Catches a regression where `build()` stops
     * calling `driverBuilder.build()` (e.g. emitting a default driver), or where a
     * specific `driver { }` setter is dropped / wired to the wrong field.
     */
    @Test
    fun `driver options set via builder propagate to config`() {
        val config = LevelDBInstanceConfig.builder {
            driver {
                createIfMissing(false)
                paranoidChecks(true)
                cacheSize(4096)
                blockSize(8192)
                writeBufferSize(16384)
            }
        }

        assertEquals(false, config.driver.createIfMissing)
        assertEquals(true, config.driver.paranoidChecks)
        assertEquals(4096, config.driver.cacheSize)
        assertEquals(8192, config.driver.blockSize)
        assertEquals(16384, config.driver.writeBufferSize)
    }

    /**
     * Pins that swapping the whole driver via `driver(config)` replaces the
     * builder's driver state wholesale. Catches a regression where the overload
     * is ignored and the default driver is built instead.
     */
    @Test
    fun `driver replaced wholesale via builder propagates to config`() {
        val replacement = LevelDBDriverConfig(
            createIfMissing = false,
            paranoidChecks = true,
            cacheSize = 1,
            blockSize = 2,
            writeBufferSize = 3,
        )

        val config = LevelDBInstanceConfig.builder {
            driver(replacement)
        }

        assertEquals(replacement, config.driver)
    }

    /**
     * Pins that the logger and factory references set through the builder land on
     * the built config. Catches a regression where `logger()` / `dbFactory()` are
     * dropped by `build()`.
     */
    @Test
    fun `logger and factory set via builder propagate to config`() {
        val logger = LevelDBConsoleLogger(printThread = true)
        val factory = LevelDBInstanceFactory { _, _ -> throw UnsupportedOperationException("test factory") }

        val config = LevelDBInstanceConfig.builder()
            .logger(logger)
            .dbFactory(factory)
            .build()

        assertSame(logger, config.logger)
        assertSame(factory, config.instanceFactory)
    }

    // ---- builder: copy-isolation ----

    /**
     * Pins that the adapter registry captured at `build()` is a snapshot: mutating
     * the builder's adapters after `build()` must not leak into the already-built
     * config. Catches a regression where `build()` stops calling
     * `adapterRegistry.copy()` and instead shares the live mutable registry.
     */
    @Test
    fun `mutating builder adapters after build does not affect config`() {
        val builder = LevelDBInstanceConfig.builder()
        val config = builder.build()

        // Sanity: Custom adapter not present in the snapshot yet (default registry never seeds it).
        assertNull(config.adapterRegistry.findAdapter(Custom::class))

        // Mutate the builder AFTER build().
        builder.adapters { addAdapter(CustomAdapter()) }

        // The already-built config must be unaffected by the post-build mutation.
        assertNull(config.adapterRegistry.findAdapter(Custom::class))
    }

    /**
     * Pins that the driver captured at `build()` is a snapshot: mutating the
     * builder's driver after `build()` must not change the already-built config.
     * Catches a regression where `build()` returns a driver that aliases mutable
     * builder state instead of `driverBuilder.build()`.
     */
    @Test
    fun `mutating builder driver after build does not affect config`() {
        val builder = LevelDBInstanceConfig.builder()
        val config = builder.build()
        val originalCacheSize = config.driver.cacheSize

        // Mutate the builder's driver AFTER build().
        builder.driver { cacheSize(originalCacheSize + 99999) }

        // The already-built config keeps the value captured at build() time.
        assertEquals(originalCacheSize, config.driver.cacheSize)
    }

    /**
     * Pins that two configs built from the same builder do not share adapter
     * state. Catches a regression where `build()` hands out the same mutable
     * registry instance, so mutating one config's registry corrupts the other.
     */
    @Test
    fun `configs built from same builder have independent adapter registries`() {
        val builder = LevelDBInstanceConfig.builder()
        val first = builder.build()
        val second = builder.build()

        first.adapterRegistry.addAdapter(CustomAdapter())

        assertNull(second.adapterRegistry.findAdapter(Custom::class))
    }

    // ---- toBuilder() round-trip ----

    /**
     * Pins that `toBuilder().build()` reproduces an equivalent config: logger and
     * factory references are preserved and every driver field round-trips. Catches
     * a regression where `toBuilder()` (the `Builder(config)` constructor) drops a
     * field or rebuilds it from defaults.
     */
    @Test
    fun `toBuilder round-trips logger factory and driver`() {
        val logger = LevelDBConsoleLogger(printThread = true)
        val factory = LevelDBInstanceFactory { _, _ -> throw UnsupportedOperationException("test factory") }
        val original = LevelDBInstanceConfig.builder()
            .logger(logger)
            .dbFactory(factory)
            .driver {
                createIfMissing(false)
                paranoidChecks(true)
                cacheSize(111)
                blockSize(222)
                writeBufferSize(333)
            }
            .build()

        val roundTripped = original.toBuilder().build()

        assertSame(logger, roundTripped.logger)
        assertSame(factory, roundTripped.instanceFactory)
        assertEquals(original.driver, roundTripped.driver)
    }

    /**
     * Pins that `toBuilder()` carries the original's adapters into the rebuilt
     * config. Catches a regression where `toBuilder()` resets the registry to the
     * defaults instead of copying the original's adapters.
     */
    @Test
    fun `toBuilder preserves registered adapters`() {
        val intAdapter = IntStringAdapter()
        val original = LevelDBInstanceConfig.builder()
            .adapters { addAdapter(intAdapter) }
            .build()

        val roundTripped = original.toBuilder().build()

        assertSame(intAdapter, roundTripped.adapterRegistry.findAdapter(Int::class))
    }

    /**
     * Pins that `toBuilder()` produces an isolated copy of the registry: adapters
     * added through the new builder must not leak back into the source config.
     * Catches a regression where the `Builder(config)` constructor aliases the
     * source config's mutable registry instead of copying it.
     */
    @Test
    fun `toBuilder does not leak new adapters back into source config`() {
        val original = LevelDBInstanceConfig.builder().build()
        assertNull(original.adapterRegistry.findAdapter(Custom::class))

        original.toBuilder()
            .adapters { addAdapter(CustomAdapter()) }
            .build()

        // The source config's registry must be untouched.
        assertNull(original.adapterRegistry.findAdapter(Custom::class))
    }

    /**
     * Pins that round-tripping through `toBuilder()` without changes preserves the
     * default driver values (the no-op identity round trip), guarding against a
     * silent default drift in the `Builder(config)` constructor.
     */
    @Test
    fun `toBuilder no-op round trip preserves defaults`() {
        val original = LevelDBInstanceConfig.builder().build()

        val roundTripped = original.toBuilder().build()

        assertEquals(original.driver, roundTripped.driver)
        assertSame(LevelDBNullLogger, roundTripped.logger)
        assertTrue(roundTripped.driver.createIfMissing)
    }

    // ---- copy-isolation: clear / remove mutations (both leak directions) ----

    /**
     * Pins that clearing the builder's registry after `build()` does not wipe the
     * already-built config's adapters. Catches a regression where `build()` shares
     * the live mutable registry instead of calling `adapterRegistry.copy()` (the
     * `copy()` at build() time): a shared map would let `clearAdapters()` on the
     * builder empty the snapshot the config already handed out. The add-only suite
     * cannot see this because clear mutates an *existing* key set rather than
     * inserting a sentinel.
     */
    @Test
    fun `clearing builder adapters after build does not empty config registry`() {
        val builder = LevelDBInstanceConfig.builder()
            .adapters { addAdapter(CustomAdapter()) }
        val config = builder.build()

        // The built snapshot has the Custom adapter.
        assertNotNull(config.adapterRegistry.findAdapter(Custom::class))

        // Clear the builder's registry AFTER build().
        builder.adapters { clearAdapters() }

        // A shared map would now make this null; an isolated copy keeps it.
        assertNotNull(config.adapterRegistry.findAdapter(Custom::class))
    }

    /**
     * Pins that removing a single adapter from the builder after `build()` does not
     * remove it from the already-built config. Catches the narrower shared-map
     * regression (`build()` shares the registry AND `removeAdapter()` mutates it)
     * that an add-only suite never exercises.
     */
    @Test
    fun `removing builder adapter after build does not affect config`() {
        val builder = LevelDBInstanceConfig.builder()
            .adapters { addAdapter(CustomAdapter()) }
        val config = builder.build()

        assertNotNull(config.adapterRegistry.findAdapter(Custom::class))

        builder.adapters { removeAdapter(Custom::class) }

        assertNotNull(config.adapterRegistry.findAdapter(Custom::class))
    }

    /**
     * Pins config<->config isolation for the *clear* mutation: clearing one built
     * config's registry must not empty a sibling built from the same builder.
     * Catches a regression where `build()` hands out the same mutable registry
     * instance to every config (a shared map would make the sibling lookup null).
     */
    @Test
    fun `clearing one config registry does not empty a sibling config`() {
        val builder = LevelDBInstanceConfig.builder()
            .adapters { addAdapter(CustomAdapter()) }
        val first = builder.build()
        val second = builder.build()

        first.adapterRegistry.clearAdapters()

        assertNotNull(second.adapterRegistry.findAdapter(Custom::class))
    }

    /**
     * Pins config<->config isolation for the *remove* mutation: removing an adapter
     * from one built config must not remove it from a sibling. Catches the shared
     * registry regression on the removal path specifically.
     */
    @Test
    fun `removing adapter from one config does not affect a sibling config`() {
        val builder = LevelDBInstanceConfig.builder()
            .adapters { addAdapter(CustomAdapter()) }
        val first = builder.build()
        val second = builder.build()

        first.adapterRegistry.removeAdapter(Custom::class)

        assertNotNull(second.adapterRegistry.findAdapter(Custom::class))
    }

    /**
     * Pins that clearing a built config's registry does not corrupt the builder it
     * came from: a later `build()` still produces a populated registry. Catches a
     * regression where `build()` returns the builder's live registry, so clearing
     * the config's registry would also empty the builder's source map and poison
     * every subsequent build.
     */
    @Test
    fun `clearing built config registry does not corrupt the builder`() {
        val builder = LevelDBInstanceConfig.builder()
            .adapters { addAdapter(CustomAdapter()) }
        val first = builder.build()

        first.adapterRegistry.clearAdapters()

        val second = builder.build()
        assertNotNull(second.adapterRegistry.findAdapter(Custom::class))
    }

    // ---- toBuilder(): reverse leak direction (source -> builder/config) ----

    /**
     * Pins the reverse leak direction of [toBuilder]: mutating the SOURCE config's
     * registry after `toBuilder()` must not bleed into the config rebuilt from that
     * builder. Catches a regression where the `Builder(config)` constructor aliases
     * the source config's mutable registry (drops the `.copy()` at line 63), which
     * the forward-only `toBuilder does not leak ... back` test cannot detect.
     */
    @Test
    fun `mutating source config after toBuilder does not leak into rebuilt config`() {
        val original = LevelDBInstanceConfig.builder().build()
        val builder = original.toBuilder()

        // Mutate the SOURCE config's registry AFTER snapshotting it into a builder.
        original.adapterRegistry.addAdapter(CustomAdapter())

        val rebuilt = builder.build()

        // An aliased source map would surface the post-snapshot Custom adapter here.
        assertNull(rebuilt.adapterRegistry.findAdapter(Custom::class))
    }

    /**
     * Pins the reverse leak direction for the *clear* mutation: clearing the source
     * config's registry after `toBuilder()` must not empty the builder's snapshot.
     * Catches the aliased-source-map regression on the clear path (the add-direction
     * test only proves insertions do not bleed).
     */
    @Test
    fun `clearing source config after toBuilder does not empty rebuilt config`() {
        val intAdapter = IntStringAdapter()
        val original = LevelDBInstanceConfig.builder()
            .adapters { addAdapter(intAdapter) }
            .build()
        val builder = original.toBuilder()

        original.adapterRegistry.clearAdapters()

        val rebuilt = builder.build()
        assertSame(intAdapter, rebuilt.adapterRegistry.findAdapter(Int::class))
    }

    // ---- toBuilder(): default instanceFactory round-trip ----

    /**
     * Pins that the DEFAULT instanceFactory survives `toBuilder()`: the exact
     * factory reference held by a default-built config must be the same instance on
     * the rebuilt config. Catches a regression where `Builder(config)` drops
     * `instanceFactory` and rebuilds a fresh `NativeLevelDB` factory — invisible to
     * tests that only assertSame on an explicitly-set custom factory.
     */
    @Test
    fun `toBuilder preserves the default instance factory reference`() {
        val original = LevelDBInstanceConfig.builder().build()

        val roundTripped = original.toBuilder().build()

        assertSame(original.instanceFactory, roundTripped.instanceFactory)
    }

    /**
     * Pins that the DEFAULT logger reference (LevelDBNullLogger) survives
     * `toBuilder()` by reference, not merely by equality. Complements the no-op
     * default round trip which asserts the singleton but not that the rebuild path
     * carried the *original* reference forward.
     */
    @Test
    fun `toBuilder preserves the default logger reference`() {
        val original = LevelDBInstanceConfig.builder().build()

        val roundTripped = original.toBuilder().build()

        assertSame(original.logger, roundTripped.logger)
    }

    // ---- driver knob Int boundaries (no clamp / no overflow) ----

    /**
     * Pins that the driver Int knobs accept boundary values verbatim through the
     * builder DSL — no clamp-to-zero, no overflow-wrap. Catches a regression where
     * any setter coerces or clamps its input (e.g. `cacheSize(max(0, value))`),
     * which the happy-path test using small positive values cannot detect.
     */
    @Test
    fun `driver knobs propagate Int boundary values without clamping`() {
        val config = LevelDBInstanceConfig.builder {
            driver {
                cacheSize(Int.MAX_VALUE)
                blockSize(Int.MIN_VALUE)
                writeBufferSize(-1)
            }
        }

        assertEquals(Int.MAX_VALUE, config.driver.cacheSize)
        assertEquals(Int.MIN_VALUE, config.driver.blockSize)
        assertEquals(-1, config.driver.writeBufferSize)
    }

    /**
     * Pins that boundary Int values survive a `toBuilder()` round trip field-by-field
     * (read independently, not via data-class equals). Catches a regression where
     * `Builder(config)` truncates/clamps a single knob during the round trip *and* a
     * hypothetical equals() that ignores that field would otherwise mask it.
     */
    @Test
    fun `driver knobs round-trip Int boundary values through toBuilder`() {
        val original = LevelDBInstanceConfig.builder {
            driver {
                cacheSize(Int.MAX_VALUE)
                blockSize(Int.MIN_VALUE)
                writeBufferSize(Int.MAX_VALUE - 1)
            }
        }

        val driver = original.toBuilder().build().driver

        assertEquals(Int.MAX_VALUE, driver.cacheSize)
        assertEquals(Int.MIN_VALUE, driver.blockSize)
        assertEquals(Int.MAX_VALUE - 1, driver.writeBufferSize)
    }

    // ---- driver overload ordering / interaction ----

    /**
     * Pins the interaction edge: a wholesale `driver(config)` swap followed by a
     * `driver { }` DSL mutation composes the DSL onto the SWAPPED state, not onto a
     * fresh default builder. The DSL only touches `cacheSize`; the other fields must
     * retain the replacement's values. Catches a regression where `driver(config)`
     * resets `driverBuilder` to defaults (instead of `Builder(config)`), which is
     * invisible unless a subsequent `driver { }` call re-reads the untouched fields.
     */
    @Test
    fun `driver DSL after wholesale swap composes onto swapped state`() {
        val replacement = LevelDBDriverConfig(
            createIfMissing = false,
            paranoidChecks = true,
            cacheSize = 100,
            blockSize = 200,
            writeBufferSize = 300,
        )

        val config = LevelDBInstanceConfig.builder {
            driver(replacement)
            driver { cacheSize(999) }
        }

        // The DSL overrode only cacheSize...
        assertEquals(999, config.driver.cacheSize)
        // ...and the swapped-in fields it never touched must survive (not reset to defaults).
        assertEquals(false, config.driver.createIfMissing)
        assertEquals(true, config.driver.paranoidChecks)
        assertEquals(200, config.driver.blockSize)
        assertEquals(300, config.driver.writeBufferSize)
    }

    /**
     * Pins the reverse ordering: a `driver { }` DSL mutation followed by a wholesale
     * `driver(config)` swap discards the earlier DSL state entirely. Catches a
     * regression where `driver(config)` merges onto (instead of replacing) the
     * existing driver builder, so the earlier `cacheSize(777)` would survive.
     */
    @Test
    fun `wholesale swap after driver DSL discards earlier DSL state`() {
        val replacement = LevelDBDriverConfig(
            createIfMissing = true,
            paranoidChecks = false,
            cacheSize = 5,
            blockSize = 6,
            writeBufferSize = 7,
        )

        val config = LevelDBInstanceConfig.builder {
            driver { cacheSize(777) }
            driver(replacement)
        }

        assertEquals(replacement, config.driver)
    }

    // ---- duplicate-adapter (last-write-wins) on the builder path ----

    /**
     * Pins last-write-wins for duplicate adapters of the same type registered
     * through the builder, and that `build()`'s copy reflects the override. Catches
     * a regression where `addAdapter` for an existing key is ignored (first-write-
     * wins) or where build()'s copy snapshots a stale entry.
     */
    @Test
    fun `duplicate adapter registration is last-write-wins through builder`() {
        val first = CustomAdapter()
        val second = CustomAdapter()

        val config = LevelDBInstanceConfig.builder()
            .adapters {
                addAdapter(first)
                addAdapter(second)
            }
            .build()

        assertSame(second, config.adapterRegistry.findAdapter(Custom::class))
    }

    // ---- cleared-registry round-trip (empty-map copy edge) ----

    /**
     * Pins the empty/cleared-registry round trip: a config whose registry was
     * cleared still round-trips through `toBuilder()` as empty (the default adapters
     * are NOT silently re-seeded). Catches a regression where `Builder(config)` or
     * `build()` re-initializes a default registry when the source map is empty
     * (a real edge because copying an empty map is indistinguishable from "no
     * adapters configured" only if the code special-cases emptiness).
     */
    @Test
    fun `cleared registry round-trips as empty through toBuilder`() {
        val original = LevelDBInstanceConfig.builder().build()
        original.adapterRegistry.clearAdapters()

        // Pick a type the default registry seeds so re-seeding would resurrect it.
        val seededType = original.toBuilder() // snapshot the cleared state
            .build()

        // Cleared means no Int adapter (defaults seed common types); must stay absent.
        assertNull(seededType.adapterRegistry.findAdapter(Int::class))
        assertTrue(seededType.adapterRegistry.adapters.isEmpty())
    }
}
