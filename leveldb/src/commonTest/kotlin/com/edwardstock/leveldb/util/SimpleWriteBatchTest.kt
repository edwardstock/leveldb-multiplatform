package com.edwardstock.leveldb.util

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.WriteBatch
import com.edwardstock.leveldb.api.del
import com.edwardstock.leveldb.api.putBytes
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.api.putValue
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.impl.SimpleWriteBatch
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Original author: Stojan Dimitrovski <sdimitrovski@gmail.com>
 * Modified by: Eduard Maximovich <edward.vstock@gmail.com>
 *
 * (Original BSD 3-Clause License follows)
 *
 * Copyright (c) 2014, Stojan Dimitrovski <sdimitrovski@gmail.com>
 *
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 */
class SimpleWriteBatchTest : DatabaseTestCase() {
    @Test
    @Throws(Exception::class)
    fun testOperations() {
        val writeBatch = SimpleWriteBatch()
        writeBatch.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3))
        assertEquals(1, writeBatch.allOperations.size)

        writeBatch.del(byteArrayOf(1, 2, 3))
        assertEquals(2, writeBatch.allOperations.size)

        var del = 0
        var put = 0
        for (operation in writeBatch) {
            if (operation.isPut) {
                put++
            } else if (operation.isDel) {
                del++
            }
            assertNotNull(operation.key())
            if (!operation.isDel) {
                assertNotNull(operation.value())
            }
        }
        assertEquals(1, del)
        assertEquals(1, put)
        assertEquals(2, writeBatch.allOperations.size)
    }

    /**
     * Pins that the order operations are queued in is preserved by allOperations and by the
     * Iterable contract. SimpleWriteBatch backs operations with a List; an implementation that
     * switched to an unordered/sorted container (e.g. sorting by key for write efficiency) would
     * reorder these and fail. Keys are queued out of lexicographic order on purpose ("c","a","b")
     * to distinguish "insertion order" from "sorted order".
     */
    @Test
    fun queuedOperationsPreserveInsertionOrder() {
        val batch = SimpleWriteBatch()
        batch.put("c".encodeToByteArray(), "3".encodeToByteArray())
        batch.put("a".encodeToByteArray(), "1".encodeToByteArray())
        batch.del("b".encodeToByteArray())

        val keys = batch.allOperations.map { it.key().decodeToString() }
        assertContentEquals(listOf("c", "a", "b"), keys)

        // iterator() must yield the same order as allOperations.
        val iterKeys = batch.map { it.key().decodeToString() }
        assertContentEquals(listOf("c", "a", "b"), iterKeys)
    }

    /**
     * Pins the core WriteBatch invariant: put(key, value=null) is equivalent to del(key) for both
     * the ByteArray overload (SimpleWriteBatch.put / WriteBatch.put) and the String overload
     * (SimpleWriteBatch.put(String, String?)). The resulting operation must be a delete
     * (isDel == true, value() == null), not a put storing a null/empty value.
     *
     * If the production put(...) branch that maps null to del() were removed, the operation would
     * come back as a put with a null value and these assertions would fail.
     */
    @Test
    fun putWithNullValueIsDelete() {
        // ByteArray overload
        val bytesBatch = SimpleWriteBatch()
        bytesBatch.put("k".encodeToByteArray(), null)
        assertEquals(1, bytesBatch.allOperations.size)
        val byteOp = bytesBatch.allOperations.single()
        assertTrue(byteOp.isDel, "put(ByteArray, null) must produce a delete")
        assertFalse(byteOp.isPut)
        assertNull(byteOp.value())
        assertContentEquals("k".encodeToByteArray(), byteOp.key())

        // String overload
        val stringBatch = SimpleWriteBatch()
        stringBatch.put("k", null)
        assertEquals(1, stringBatch.allOperations.size)
        val strOp = stringBatch.allOperations.single()
        assertTrue(strOp.isDel, "put(String, null) must produce a delete")
        assertFalse(strOp.isPut)
        assertNull(strOp.value())
        assertContentEquals("k".encodeToByteArray(), strOp.key())
    }

    /**
     * Pins that a non-null empty value is a PUT, not a delete. This is the boundary partner of
     * [putWithNullValueIsDelete]: null means delete, ByteArray(0) means "put an empty value".
     * An implementation that treated empty-as-null (e.g. `value?.takeIf { it.isNotEmpty() }`)
     * would mis-classify this as a delete and fail.
     */
    @Test
    fun putWithEmptyValueIsPut() {
        val batch = SimpleWriteBatch()
        batch.put("k".encodeToByteArray(), ByteArray(0))
        val op = batch.allOperations.single()
        assertTrue(op.isPut, "put(ByteArray, ByteArray(0)) must produce a put")
        assertFalse(op.isDel)
        assertNotNull(op.value())
        assertContentEquals(ByteArray(0), op.value())
    }

    /**
     * Pins insert(): an externally supplied Operation is appended verbatim, in order, and is
     * reflected in both allOperations and the iterator. We obtain a real Operation instance via a
     * helper batch (the concrete Operation type is private) and insert it into a fresh batch.
     * If insert() dropped or reordered operations, the key/order assertions would fail.
     */
    @Test
    fun insertAppendsOperationInOrder() {
        val source = SimpleWriteBatch()
        source.put("x".encodeToByteArray(), "X".encodeToByteArray())
        source.del("y".encodeToByteArray())
        val putOp = source.allOperations.first { it.isPut }
        val delOp = source.allOperations.first { it.isDel }

        val target = SimpleWriteBatch()
        target.del("z".encodeToByteArray())
        target.insert(putOp)
        target.insert(delOp)

        val ops = target.allOperations.toList()
        assertEquals(3, ops.size)
        assertContentEquals(
            listOf("z", "x", "y"),
            ops.map { it.key().decodeToString() },
        )
        assertTrue(ops[0].isDel)
        assertTrue(ops[1].isPut)
        assertContentEquals("X".encodeToByteArray(), ops[1].value())
        assertTrue(ops[2].isDel)
    }

    /**
     * Pins remove(): removing a queued operation (by value equality) drops exactly that operation
     * and leaves the rest in order. remove() delegates to List.remove, which uses Operation.equals
     * (type + key + value). If remove() became a no-op or removed the wrong entry, the remaining
     * keys would not match.
     */
    @Test
    fun removeDropsMatchingOperation() {
        val batch = SimpleWriteBatch()
        batch.put("a".encodeToByteArray(), "1".encodeToByteArray())
        batch.put("b".encodeToByteArray(), "2".encodeToByteArray())
        batch.del("c".encodeToByteArray())
        assertEquals(3, batch.allOperations.size)

        val toRemove = batch.allOperations.first { it.key().decodeToString() == "b" }
        batch.remove(toRemove)

        val remaining = batch.allOperations.map { it.key().decodeToString() }
        assertContentEquals(listOf("a", "c"), remaining)
    }

    /**
     * Pins remove() positional correctness against the `operations.removeAt(0)` mutation.
     *
     * removeDropsMatchingOperation only removes the MIDDLE element of a 3-element batch — but a
     * `removeAt(0)` mutant happens to leave a plausible-looking remainder there too. Here we remove
     * the operation whose key is "c" (the LAST queued op). With correct value-equality removal the
     * remainder is ["a","b"]; a `removeAt(0)` mutant would instead drop "a" and leave ["b","c"],
     * failing this assertion. This nails that remove() targets the matched operation, not a fixed
     * index.
     */
    @Test
    fun removeTargetsMatchedOperationNotFirstIndex() {
        val batch = SimpleWriteBatch()
        batch.put("a".encodeToByteArray(), "1".encodeToByteArray())
        batch.put("b".encodeToByteArray(), "2".encodeToByteArray())
        batch.del("c".encodeToByteArray())

        val last = batch.allOperations.first { it.key().decodeToString() == "c" }
        batch.remove(last)

        assertContentEquals(
            listOf("a", "b"),
            batch.allOperations.map { it.key().decodeToString() },
        )
    }

    /**
     * Pins that remove() of an operation NOT present in the batch is a no-op (List.remove returns
     * false and leaves the list untouched). A mutant that, say, always removed the first element on
     * any remove() call would shrink the batch here and fail.
     */
    @Test
    fun removeOfAbsentOperationIsNoOp() {
        val target = SimpleWriteBatch()
        target.put("a".encodeToByteArray(), "1".encodeToByteArray())
        target.put("b".encodeToByteArray(), "2".encodeToByteArray())

        // Build an equal-by-value op that was never queued into `target`.
        val foreign = SimpleWriteBatch().apply {
            put("z".encodeToByteArray(), "9".encodeToByteArray())
        }.allOperations.single()

        target.remove(foreign)

        assertContentEquals(
            listOf("a", "b"),
            target.allOperations.map { it.key().decodeToString() },
        )
    }

    /**
     * Pins remove() with two value-EQUAL operations queued. List.remove drops only the FIRST
     * matching element, leaving the second copy in place. Two identical puts on key "d" are queued
     * around a put on "e"; removing one equal op must leave exactly one "d" plus "e", in order.
     *
     * This distinguishes correct single-removal (List.remove) from a mutant that removed ALL equal
     * matches (would leave only "e") or removed by a fixed index.
     */
    @Test
    fun removeDropsOnlyFirstOfTwoEqualOperations() {
        val batch = SimpleWriteBatch()
        batch.put("d".encodeToByteArray(), "4".encodeToByteArray())
        batch.put("e".encodeToByteArray(), "5".encodeToByteArray())
        batch.put("d".encodeToByteArray(), "4".encodeToByteArray()) // value-equal to the first

        // The two "d" ops are equal by (type,key,value), so allOperations.first matches the first.
        val dOp = batch.allOperations.first { it.key().decodeToString() == "d" }
        batch.remove(dOp)

        val keys = batch.allOperations.map { it.key().decodeToString() }
        // Exactly one "d" survives, "e" untouched, order preserved.
        assertEquals(2, keys.size)
        assertEquals(1, keys.count { it == "d" }, "only one of the two equal 'd' ops must be removed")
        assertTrue(keys.contains("e"))
    }

    /**
     * Pins insert() of a FOREIGN Operation implementation whose isPut/isDel come from the
     * WriteBatch.Operation interface DEFAULTS (value-derived: non-null => put, null => del), rather
     * than SimpleWriteBatch's private type-derived override. insert() must append it verbatim and
     * preserve the interface-default classification.
     *
     * If insert() re-wrapped or re-derived the operation type from something other than the value,
     * the foreign put (non-null value) or foreign delete (null value) would be misclassified and
     * these isPut/isDel assertions would fail.
     */
    @Test
    fun insertForeignOperationKeepsInterfaceDefaultClassification() {
        // Anonymous Operation using ONLY the interface (no isPut/isDel override).
        val foreignPut = object : WriteBatch.Operation {
            override fun key(): ByteArray = "fk".encodeToByteArray()
            override fun value(): ByteArray = "fv".encodeToByteArray()
        }
        val foreignDel = object : WriteBatch.Operation {
            override fun key(): ByteArray = "gk".encodeToByteArray()
            override fun value(): ByteArray? = null
        }

        val batch = SimpleWriteBatch()
        batch.insert(foreignPut)
        batch.insert(foreignDel)

        val ops = batch.allOperations.toList()
        assertEquals(2, ops.size)

        // First: non-null value => interface default isPut == true, isDel == false.
        assertTrue(ops[0].isPut, "foreign op with non-null value must report isPut")
        assertFalse(ops[0].isDel)
        assertContentEquals("fk".encodeToByteArray(), ops[0].key())
        assertContentEquals("fv".encodeToByteArray(), ops[0].value())

        // Second: null value => interface default isDel == true, isPut == false.
        assertTrue(ops[1].isDel, "foreign op with null value must report isDel")
        assertFalse(ops[1].isPut)
        assertContentEquals("gk".encodeToByteArray(), ops[1].key())
        assertNull(ops[1].value())
    }

    /**
     * Pins the WriteBatch.* EXTENSION overloads in LevelDBExtensions.kt (putString / putBytes /
     * putValue), which the member-overload tests above never exercise. These extensions take a
     * String key and must:
     *   - putString(key, value): queue a PUT with UTF-8 encoded key+value;
     *   - putString(key, null): map to del (null => delete);
     *   - putBytes(key, value): queue a PUT;
     *   - putBytes(key, null): map to del (delegates to put(key, null) -> del);
     *   - putValue(key, value, T): encode via adapter and queue a PUT;
     *   - putValue(key, null, T): map to del.
     *
     * If any of these extensions broke the null->del mapping (e.g. `put(key, value ?: ByteArray(0))`)
     * the corresponding operation would come back as a PUT and the isDel assertion would fail.
     */
    @Test
    fun writeBatchExtensionOverloadsMapValuesAndNullToDelete() {
        val batch = SimpleWriteBatch(LevelDBInstanceConfig())

        batch.putString("s", "sv")          // -> PUT "s"/"sv"
        batch.putString("sNull", null)        // -> DEL "sNull"
        batch.putBytes("b", byteArrayOf(7, 8)) // -> PUT "b"
        batch.putBytes("bNull", null)         // -> DEL "bNull"
        batch.putValue("i", 42, Int::class)   // -> PUT "i" (Int adapter is registered by default)
        batch.putValue("iNull", null, Int::class) // -> DEL "iNull"

        val ops = batch.allOperations.toList()
        assertEquals(6, ops.size)

        fun op(key: String) = ops.first { it.key().decodeToString() == key }

        val sPut = op("s")
        assertTrue(sPut.isPut)
        assertContentEquals("sv".encodeToByteArray(), sPut.value())

        assertTrue(op("sNull").isDel, "putString(key, null) extension must produce a delete")
        assertNull(op("sNull").value())

        val bPut = op("b")
        assertTrue(bPut.isPut)
        assertContentEquals(byteArrayOf(7, 8), bPut.value())

        assertTrue(op("bNull").isDel, "putBytes(key, null) extension must produce a delete")
        assertNull(op("bNull").value())

        val iPut = op("i")
        assertTrue(iPut.isPut)
        assertContentEquals(
            LevelDBInstanceConfig().adapterRegistry.encode(42, Int::class),
            iPut.value(),
        )

        assertTrue(op("iNull").isDel, "putValue(key, null) extension must produce a delete")
        assertNull(op("iNull").value())
    }

    /**
     * Pins an EMPTY key (ByteArray(0)) on both put and del paths: an empty key is a legal,
     * addressable key and must be queued verbatim, not dropped or rejected. put(empty, value)
     * produces a PUT with an empty key; del(empty) produces a DEL with an empty key.
     */
    @Test
    fun emptyKeyIsQueuedForPutAndDel() {
        val batch = SimpleWriteBatch()
        batch.put(ByteArray(0), "v".encodeToByteArray())
        batch.del(ByteArray(0))

        val ops = batch.allOperations.toList()
        assertEquals(2, ops.size)

        assertTrue(ops[0].isPut)
        assertContentEquals(ByteArray(0), ops[0].key())
        assertContentEquals("v".encodeToByteArray(), ops[0].value())

        assertTrue(ops[1].isDel)
        assertContentEquals(ByteArray(0), ops[1].key())
        assertNull(ops[1].value())
    }

    /**
     * Pins duplicate-key semantics within a single batch: SimpleWriteBatch is an ordered LOG, not a
     * map. Two puts on the same key, then a del on that same key, must all COEXIST in queue order
     * (no dedup, no collapse, no reordering). A mutant that backed operations with a Map keyed by
     * `key` (collapsing duplicates) would lose entries and fail the size/order assertions.
     */
    @Test
    fun duplicateKeysCoexistInQueueOrder() {
        val batch = SimpleWriteBatch()
        batch.put("k".encodeToByteArray(), "1".encodeToByteArray())
        batch.put("k".encodeToByteArray(), "2".encodeToByteArray())
        batch.del("k".encodeToByteArray())

        val ops = batch.allOperations.toList()
        assertEquals(3, ops.size, "duplicate keys must not be collapsed")

        assertTrue(ops[0].isPut)
        assertContentEquals("1".encodeToByteArray(), ops[0].value())
        assertTrue(ops[1].isPut)
        assertContentEquals("2".encodeToByteArray(), ops[1].value())
        assertTrue(ops[2].isDel)
        assertNull(ops[2].value())

        // All three address the same key, in queue order.
        assertContentEquals(
            listOf("k", "k", "k"),
            ops.map { it.key().decodeToString() },
        )
    }

    /**
     * Pins that a large key and large value round-trip through the batch verbatim (no truncation in
     * key()/value()). The existing forEach tests guard valueBytes truncation on the read path; this
     * guards the WriteBatch.Operation.key()/value() accessors on the write path for an oversized key
     * (8 KiB) paired with an oversized value (64 KiB).
     */
    @Test
    fun largeKeyAndValueRoundTripVerbatim() {
        val largeKey = ByteArray(8 * 1024) { (it % 251).toByte() }
        val largeValue = ByteArray(64 * 1024) { ((it * 7) % 251).toByte() }

        val batch = SimpleWriteBatch()
        batch.put(largeKey, largeValue)

        val op = batch.allOperations.single()
        assertTrue(op.isPut)
        assertContentEquals(largeKey, op.key())
        assertContentEquals(largeValue, op.value())
    }

    override fun obtainLevelDB(): LevelDB {
        return db
    }
}
