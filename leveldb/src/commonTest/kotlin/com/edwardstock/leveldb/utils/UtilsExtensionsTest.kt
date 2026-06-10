package com.edwardstock.leveldb.utils

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.del
import com.edwardstock.leveldb.api.forEachAll
import com.edwardstock.leveldb.api.forEachAllKeyString
import com.edwardstock.leveldb.api.forEachAllValueString
import com.edwardstock.leveldb.api.forEachPrefix
import com.edwardstock.leveldb.api.getBytes
import com.edwardstock.leveldb.api.open
import com.edwardstock.leveldb.api.putBytes
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.config.LevelDBDriverConfig
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UtilsExtensionsTest {
    /**
     * Pins that forEachAll / forEachAllKeyString / forEachAllValueString visit entries in
     * LevelDB iterator order (lexicographic by key), not in insertion order and not in some
     * unordered set. Keys are inserted deliberately out of order ("c", "a", "b") so an
     * implementation that returned insertion order, reverse order, or skipped the
     * seekToFirst()/next() walk would produce a different list and fail here.
     *
     * The previous version of this test compared via .toSet(), which would pass even if the
     * iteration order were completely wrong — defeating the purpose of forEach.
     */
    @Test
    fun `forEach extensions iterate keys and values in lexicographic order`() = kotlinx.coroutines.test.runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val instance = LevelDBInstance.builder(path)
            .scope(this)
            .driver { LevelDBDriverConfig(createIfMissing = true) }
            .build()

        instance.use {
            // insert out of order on purpose
            putString("c", "3")
            putString("a", "1")
            putString("b", "2")
        }

        val readerAll = mutableListOf<Pair<String, String>>()
        val readerKeys = mutableListOf<String>()
        val readerValues = mutableListOf<String>()

        instance.use {
            forEachAll { readerAll += it.keyString() to it.valueString() }
            forEachAllKeyString { readerKeys += it }
            forEachAllValueString { readerValues += it }
        }

        val db = LevelDB.open(path.toString())
        val dbAll = mutableListOf<Pair<String, String>>()
        val dbKeys = mutableListOf<String>()
        val dbValues = mutableListOf<String>()

        db.forEachAll { dbAll += it.keyString() to it.valueString() }
        db.forEachAllKeyString { dbKeys += it }
        db.forEachAllValueString { dbValues += it }
        db.close()

        // Ordered assertions: lexicographic key order regardless of insertion order.
        assertContentEquals(listOf("a", "b", "c"), readerKeys)
        assertContentEquals(listOf("1", "2", "3"), readerValues)
        assertContentEquals(listOf("a", "b", "c"), dbKeys)
        assertContentEquals(listOf("1", "2", "3"), dbValues)
        assertContentEquals(
            listOf("a" to "1", "b" to "2", "c" to "3"),
            readerAll,
        )
        // Reader (LevelDBInstance) and freshly opened DB must agree on ordered contents.
        assertContentEquals(readerAll, dbAll)

        instance.closeAndAwait()
    }

    /**
     * Pins that empty-value and large-value entries round-trip through the forEach extensions.
     *
     * - An empty value (ByteArray(0)) is a legal, distinct-from-absent value in LevelDB. A
     *   forEachAll implementation that skipped empty values, or that treated empty as a delete,
     *   would drop the "empty" key from the iteration and fail the ordered key assertion.
     * - A large value (64 KiB) guards against any truncation in valueBytes()/valueString().
     *
     * Keys are chosen so lexicographic order is empty < large < small, independent of value size.
     */
    @Test
    fun `forEach extensions handle empty and large values`() = kotlinx.coroutines.test.runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val instance = LevelDBInstance.builder(path)
            .scope(this)
            .driver { LevelDBDriverConfig(createIfMissing = true) }
            .build()

        val largeValue = ByteArray(64 * 1024) { (it % 251).toByte() }
        val largeKey = "k_large"
        val emptyKey = "k_empty"
        val smallKey = "k_small"

        instance.use {
            putBytes(emptyKey, ByteArray(0))
            putBytes(largeKey, largeValue)
            putString(smallKey, "v")
        }

        // Sanity: empty value is present and distinct from a missing key (getBytes is non-null
        // for empty value, null only if absent). This keeps the forEach expectation honest.
        instance.use {
            assertContentEquals(ByteArray(0), getBytes(emptyKey))
            assertContentEquals(largeValue, getBytes(largeKey))
        }

        val keys = mutableListOf<String>()
        val values = mutableListOf<ByteArray>()
        instance.use {
            forEachAll {
                keys += it.keyString()
                values += it.valueBytes()
            }
        }

        // Lexicographic key order: "k_empty" < "k_large" < "k_small".
        assertContentEquals(listOf(emptyKey, largeKey, smallKey), keys)
        assertEquals(3, values.size)
        assertContentEquals(ByteArray(0), values[0])
        assertContentEquals(largeValue, values[1])
        assertContentEquals("v".encodeToByteArray(), values[2])

        instance.closeAndAwait()
    }

    /**
     * Pins that forEachAll over an EMPTY database visits zero entries.
     *
     * This is the boundary that distinguishes a correct `iterator.seekToFirst()` (lands on
     * isValid==false when empty, so the while-loop body never runs) from mutations like
     * `iterator.seekToLast()` (also empty here, so this case alone would not catch it) — but
     * more importantly it is the only case that exercises the empty-DB / off-by-one boundary of
     * the seek+walk loop in forEachAllInternal. A no-DB-content scan that erroneously emitted a
     * phantom entry, or that started the walk without a valid seek, would append to `visited`
     * here and fail `assertTrue(visited.isEmpty())`. Combined with the populated-DB ordering
     * test, the seek position is pinned at both the empty and non-empty boundary.
     */
    @Test
    fun `forEachAll over empty database visits nothing`() = kotlinx.coroutines.test.runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString())
        val visited = mutableListOf<String>()
        db.forEachAll { visited += it.keyString() }
        db.close()

        assertTrue(visited.isEmpty(), "forEachAll on an empty DB must visit no entries")
    }

    /**
     * Pins that forEachAll walks keys in BYTE-lexicographic (unsigned) order, which is NOT the
     * same as Kotlin String.compareTo for these keys. Two distinctions are exercised:
     *
     *  1. High byte 0xFF: the key {0xFF} must sort AFTER {0x7F} under unsigned byte comparison.
     *     A signed-byte comparator (0xFF -> -1) would place {0xFF} first and fail.
     *  2. Length-prefix tie: {0x61} ("a") must sort before {0x61, 0x20} ("a "), because when one
     *     key is a prefix of the other the shorter sorts first. A comparator that did not handle
     *     the prefix/length tie correctly would reorder these.
     *
     * Keys are raw bytes (not single-char ASCII a/b/c), so swapping LevelDB's bytewise ordering
     * for String ordering, or for signed-byte ordering, produces a different list and fails here.
     */
    @Test
    fun `forEachAll walks keys in unsigned byte-lexicographic order`() = kotlinx.coroutines.test.runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString())

        // Expected unsigned bytewise order:
        // {0x61} < {0x61,0x20} < {0x7F} < {0x80} < {0xFF}
        val kA = byteArrayOf(0x61)              // "a"
        val kASpace = byteArrayOf(0x61, 0x20)   // "a " (a + space): shares prefix with kA
        val k7F = byteArrayOf(0x7F)
        val k80 = byteArrayOf(0x80.toByte())    // 0x80: negative as signed byte
        val kFF = byteArrayOf(0xFF.toByte())    // 0xFF: most-negative as signed byte

        // Insert in an order that is neither sorted nor reverse-sorted.
        db.putBytes(k80, byteArrayOf(4))
        db.putBytes(kA, byteArrayOf(0))
        db.putBytes(kFF, byteArrayOf(5))
        db.putBytes(kASpace, byteArrayOf(1))
        db.putBytes(k7F, byteArrayOf(3))

        val keys = mutableListOf<ByteArray>()
        db.forEachAll { keys += it.keyBytes() }
        db.close()

        val expected = listOf(kA, kASpace, k7F, k80, kFF)
        assertEquals(expected.size, keys.size)
        for (i in expected.indices) {
            assertContentEquals(expected[i], keys[i], "byte-lexicographic order mismatch at index $i")
        }
    }

    /**
     * Pins forEachAll(fillCache = true): the non-default cache-warming branch must visit the same
     * entries in the same order as the default (fillCache = false) branch. forEachAll forwards
     * fillCache into iterator(fillCache = ...). If that argument were dropped, hardcoded, or the
     * true-branch returned a differently-configured (e.g. broken) iterator, the visited list would
     * not match the expected ordered contents.
     */
    @Test
    fun `forEachAll with fillCache true visits all entries in order`() = kotlinx.coroutines.test.runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString())
        db.putString("b", "2")
        db.putString("a", "1")
        db.putString("c", "3")

        val warmed = mutableListOf<Pair<String, String>>()
        db.forEachAll(fillCache = true) { warmed += it.keyString() to it.valueString() }
        db.close()

        assertContentEquals(
            listOf("a" to "1", "b" to "2", "c" to "3"),
            warmed,
        )
    }

    /**
     * Pins forEachPrefix end to end. forEachPrefix seeks to `prefix` then walks forward while the
     * key startsWith(prefix), breaking on the first non-matching key. This single test guards the
     * whole function against the documented mutations:
     *
     *  - If `break` were dropped (walk to end of DB), `userOnly` would also pick up "userx" and
     *    every "v*" key, failing the exact-contents assertion.
     *  - If startsWith were forced to always return true, the same over-collection failure occurs.
     *  - The seek+break boundary is exercised: "user." entries sit between "us" and "userx"/"v.."
     *    keys, so a wrong seek start or a missing break is observable in the result.
     *
     * Note "userx" is intentionally NOT matched by prefix "user." (no dot), pinning that the
     * boundary is the prefix bytes, not "any key beginning with the letters user".
     */
    @Test
    fun `forEachPrefix returns only keys with the prefix in order`() = kotlinx.coroutines.test.runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString())

        // Lexicographic neighbourhood around the "user." prefix.
        db.putString("us", "x")          // before prefix, must be skipped by seek
        db.putString("user.charlie", "c")
        db.putString("user.alice", "a")
        db.putString("user.bob", "b")
        db.putString("userx", "X")        // after "user." block, must trigger break
        db.putString("zzz", "z")          // far after, must never be reached

        val matched = mutableListOf<Pair<String, String>>()
        db.forEachPrefix("user.") { matched += it.keyString() to it.valueString() }
        db.close()

        assertContentEquals(
            listOf(
                "user.alice" to "a",
                "user.bob" to "b",
                "user.charlie" to "c",
            ),
            matched,
        )
    }

    /**
     * Pins the no-match and prefix-longer-than-key boundaries of forEachPrefix.
     *
     *  - A prefix that matches no key ("zzz_") must yield an empty result. If the `break` were
     *    dropped or startsWith always returned true, the seek would land somewhere and emit
     *    entries, failing emptiness.
     *  - A prefix LONGER than every stored key ("apple_pie") must also yield nothing: startsWith
     *    returns false when prefix.size > key.size, so no key qualifies. A startsWith that did not
     *    guard the length (prefix.size > size) could index out of bounds or false-match.
     */
    @Test
    fun `forEachPrefix with no match and overlong prefix visits nothing`() = kotlinx.coroutines.test.runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString())
        db.putString("apple", "1")
        db.putString("apply", "2")
        db.putString("banana", "3")

        val noMatch = mutableListOf<String>()
        db.forEachPrefix("zzz_") { noMatch += it.keyString() }

        val overlong = mutableListOf<String>()
        db.forEachPrefix("apple_pie") { overlong += it.keyString() }

        // Sanity: a prefix that DOES match still works (guards against a vacuous always-empty bug).
        val appMatch = mutableListOf<String>()
        db.forEachPrefix("app") { appMatch += it.keyString() }
        db.close()

        assertTrue(noMatch.isEmpty(), "no-match prefix must visit nothing")
        assertTrue(overlong.isEmpty(), "prefix longer than any key must visit nothing")
        assertContentEquals(listOf("apple", "apply"), appMatch)
    }

    /**
     * Pins that an empty key (ByteArray(0)) round-trips through put and forEachAll and sorts first
     * (the empty key is the bytewise-smallest possible key). A `del(emptyKey)` then removes it,
     * confirming the empty key is a real, addressable key and not silently ignored on the write or
     * iteration path.
     */
    @Test
    fun `empty key round-trips and sorts first then can be deleted`() = kotlinx.coroutines.test.runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString())
        db.putBytes(ByteArray(0), "root".encodeToByteArray())
        db.putString("a", "1")

        val withEmpty = mutableListOf<String>()
        db.forEachAll { withEmpty += it.keyString() }
        // Empty key decodes to "" and must come first.
        assertContentEquals(listOf("", "a"), withEmpty)
        assertContentEquals("root".encodeToByteArray(), db.getBytes(ByteArray(0)))

        // del via String-extension on empty key (encodeToByteArray of "" is ByteArray(0)).
        db.del("")
        val afterDel = mutableListOf<String>()
        db.forEachAll { afterDel += it.keyString() }
        db.close()

        assertContentEquals(listOf("a"), afterDel)
    }
}
