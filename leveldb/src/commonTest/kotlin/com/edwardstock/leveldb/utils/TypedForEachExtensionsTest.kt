/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.utils

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.forEachAllKeyBytes
import com.edwardstock.leveldb.api.forEachAllKeyStringValueT
import com.edwardstock.leveldb.api.forEachAllValueBytes
import com.edwardstock.leveldb.api.forEachAllValueT
import com.edwardstock.leveldb.api.open
import com.edwardstock.leveldb.api.putValue
import com.edwardstock.leveldb.common.DatabaseTestCase
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Pins the *typed* forEachAll overloads, which decode each value through the adapter registry
 * (`valueT`) rather than handing back raw bytes/strings. These walk the same seek+next loop as the
 * untyped variants, so the assertions here focus on the decode + key-order contract:
 *
 *  - [forEachAllValueT] decodes every value to T in lexicographic key order.
 *  - [forEachAllKeyStringValueT] pairs the decoded key string with the decoded T value.
 *  - [forEachAllKeyBytes] / [forEachAllValueBytes] expose the raw bytes in the same order.
 *
 * Values are stored via the reified `putValue` (Int adapter) so the round-trip through encode +
 * decode is exercised, not just byte passthrough.
 */
class TypedForEachExtensionsTest {

    @Test
    fun `forEachAllValueT decodes values in lexicographic key order`() = kotlinx.coroutines.test.runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString())

        // Insert out of order; keys sort a < b < c.
        db.putValue("b", 20)
        db.putValue("a", 10)
        db.putValue("c", 30)

        val values = mutableListOf<Int>()
        db.forEachAllValueT<Int> { values += it }
        db.close()

        assertContentEquals(listOf(10, 20, 30), values)
    }

    @Test
    fun `forEachAllKeyStringValueT yields decoded key-value pairs in order`() = kotlinx.coroutines.test.runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString())

        db.putValue("two", 2)
        db.putValue("one", 1)
        db.putValue("three", 3)

        val pairs = mutableListOf<Pair<String, Int>>()
        db.forEachAllKeyStringValueT<Int> { key, value -> pairs += key to value }
        db.close()

        // Lexicographic: "one" < "three" < "two".
        assertContentEquals(
            listOf("one" to 1, "three" to 3, "two" to 2),
            pairs,
        )
    }

    @Test
    fun `forEachAllKeyBytes and forEachAllValueBytes walk raw bytes in key order`() = kotlinx.coroutines.test.runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString())

        db.putValue("k2", 2)
        db.putValue("k1", 1)

        val keys = mutableListOf<String>()
        db.forEachAllKeyBytes { keys += it.decodeToString() }

        val valueSizes = mutableListOf<Int>()
        db.forEachAllValueBytes { valueSizes += it.size }
        db.close()

        assertContentEquals(listOf("k1", "k2"), keys)
        // Two Int values encoded — exact size is adapter-defined, but both entries must be visited.
        assertEquals(2, valueSizes.size)
    }
}
