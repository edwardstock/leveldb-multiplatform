package com.edwardstock.leveldb.common

import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBIteratorNotValidException
import com.edwardstock.leveldb.impl.SimpleWriteBatch
import com.edwardstock.leveldb.internal.BytesHelper.lexicographicCompare
import kotlin.experimental.and
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
abstract class IterationTest : DatabaseTestCase() {
    @Test
    @Throws(Exception::class)
    fun testIteration() {
        val db = obtainLevelDB()
        val wb = SimpleWriteBatch()
        wb.put(byteArrayOf(0, 0, 1), byteArrayOf(1))
        wb.put(byteArrayOf(0, 0, 2), byteArrayOf(2))
        wb.put(byteArrayOf(0, 0, 3), byteArrayOf(3))
        db.write(wb, true)

        var iterator = db.iterator()
        db.putBytes(byteArrayOf(0, 0, 0), byteArrayOf(0))
        iterator.seekToFirst()
        var i: Byte = 1
        while (iterator.isValid) {
            val iterKey = iterator.key()
            val iterValue = iterator.value()
            assertNotNull(iterKey)
            assertEquals(0, lexicographicCompare(iterKey, byteArrayOf(0, 0, i)).toLong())
            assertEquals(0, lexicographicCompare(iterValue, byteArrayOf(i)).toLong())
            iterator.next()
            i++
        }
        assertEquals(4, (i and 0xFF.toByte()).toLong())
        iterator.close()
        iterator.close()
        iterator = db.iterator()
        iterator.seekToLast()
        assertTrue(iterator.isValid)
        i = 3
        while (iterator.isValid) {
            val key = iterator.key()
            val `val` = iterator.value()
            assertNotNull(key)
            assertEquals(0, lexicographicCompare(key, byteArrayOf(0, 0, i)).toLong())
            assertEquals(0, lexicographicCompare(`val`, byteArrayOf(i)).toLong())
            iterator.previous()
            i--
        }
        assertEquals(255L, (i.toLong() and 0xFFL))
        var threw = false
        try {
            iterator.next()
        } catch (e: LevelDBIteratorNotValidException) {
            threw = true
        }
        assertTrue(threw)
        threw = false
        try {
            iterator.previous()
        } catch (e: LevelDBIteratorNotValidException) {
            threw = true
        }
        assertTrue(threw)
        iterator.close()
        db.close()
    }

    /**
     * Arbitrary [LevelDBIterator.seek] (ceiling semantics) on a live iterator. The shared
     * [testIteration] only walks seekToFirst/seekToLast, so seek(key) is otherwise unexercised in
     * the inherited suites. This pins:
     *   - exact hit lands on the key,
     *   - seek between two stored keys lands on the next-greater key (ceiling, not floor),
     *   - seek before the first key lands on the first key,
     *   - seek past the last key leaves the iterator invalid (no clamp to lastIndex).
     *
     * Catches a Mock divergence where MockIterator.seek used floorKey instead of ceilingKey,
     * unconditionally set index=-1, or where SortedBytesStore.ceilingKey was off-by-one. For the
     * native driver it pins the C++ Seek() ceiling contract identically.
     */
    @Test
    @Throws(Exception::class)
    fun testSeekCeilingSemantics() {
        val db = obtainLevelDB()
        db.putBytes(byteArrayOf(2), byteArrayOf(20))
        db.putBytes(byteArrayOf(4), byteArrayOf(40))
        db.putBytes(byteArrayOf(6), byteArrayOf(60))

        db.iterator().use { it ->
            // Exact hit.
            it.seek(byteArrayOf(4))
            assertTrue(it.isValid)
            assertContentEquals(byteArrayOf(4), it.key())
            assertContentEquals(byteArrayOf(40), it.value())

            // Between 4 and 6 -> ceiling is 6 (NOT floor 4).
            it.seek(byteArrayOf(5))
            assertTrue(it.isValid)
            assertContentEquals(byteArrayOf(6), it.key())
            assertContentEquals(byteArrayOf(60), it.value())

            // Before the first key -> first key.
            it.seek(byteArrayOf(1))
            assertTrue(it.isValid)
            assertContentEquals(byteArrayOf(2), it.key())

            // Past the last key -> invalid (must not clamp onto key 6).
            it.seek(byteArrayOf(7))
            assertFalse(it.isValid)
        }
        db.close()
    }

    /**
     * [LevelDBIterator.previous] after an arbitrary [LevelDBIterator.seek] must step to the
     * immediately smaller key (reverse traversal from a seeked position), and stepping previous
     * off the front must invalidate the iterator. The shared [testIteration] only walks previous()
     * from seekToLast, never from a seek() landing point — a Mock off-by-one in previous() relative
     * to a seeked index would slip past it.
     */
    @Test
    @Throws(Exception::class)
    fun testPreviousAfterSeek() {
        val db = obtainLevelDB()
        db.putBytes(byteArrayOf(2), byteArrayOf(20))
        db.putBytes(byteArrayOf(4), byteArrayOf(40))
        db.putBytes(byteArrayOf(6), byteArrayOf(60))

        db.iterator().use { it ->
            it.seek(byteArrayOf(6))
            assertContentEquals(byteArrayOf(6), it.key())
            it.previous()
            assertTrue(it.isValid)
            assertContentEquals(byteArrayOf(4), it.key())
            assertContentEquals(byteArrayOf(40), it.value())
            it.previous()
            assertTrue(it.isValid)
            assertContentEquals(byteArrayOf(2), it.key())
            it.previous()
            // Stepped off the front: invalid.
            assertFalse(it.isValid)
        }
        db.close()
    }

    /**
     * Forward exhaustion: stepping [LevelDBIterator.next] off the last element must invalidate the
     * iterator (index -> "not valid"), NOT clamp onto the last element. A clamp-instead-of-invalidate
     * mutation makes the standard `while (isValid) { ...; next() }` loop in [testIteration] spin
     * forever (caught only by a timeout/hang, not an assertion); this asserts the boundary directly
     * with a bounded number of next() calls so the divergence fails fast.
     */
    @Test
    @Throws(Exception::class)
    fun testNextPastLastInvalidates() {
        val db = obtainLevelDB()
        db.putBytes(byteArrayOf(1), byteArrayOf(10))
        db.putBytes(byteArrayOf(2), byteArrayOf(20))

        db.iterator().use { it ->
            it.seekToLast()
            assertTrue(it.isValid)
            assertContentEquals(byteArrayOf(2), it.key())
            it.next()
            // Off the end -> must be invalid (no clamp back onto key 2).
            assertFalse(it.isValid)
        }
        db.close()
    }

    /**
     * Ceiling seek and full forward/reverse traversal over keys whose first byte is >= 0x80, including
     * 0xFF. The shared [testSeekCeilingSemantics]/[testIteration] only use bytes < 0x80, where signed
     * and unsigned byte comparison agree, so an UNSIGNED-vs-signed ordering bug in the comparator, in
     * SortedBytesStore.insertionPointFor/ceilingKey, or in MockIterator.seek's high-bit handling is
     * invisible to them.
     *
     * Stored unsigned order is: 0x10, 0x80, 0xC0, 0xFF.
     *
     * Catches:
     *   - signed lexicographicCompare (drop of `and 0xFF`): 0x80/0xC0/0xFF become negative and sort
     *     before 0x10, so seekToFirst lands on 0x80 not 0x10 and the ordered walk is wrong;
     *   - a floor-style off-by-one in insertionPointFor/ceilingKey for high bytes: seek(0x90) would
     *     land on 0x80 (floor) instead of 0xC0 (ceiling);
     *   - MockIterator.seek skipping keys with the high bit set: seek(0x80) would miss the exact key.
     */
    @Test
    @Throws(Exception::class)
    fun testHighByteSeekAndTraversal() {
        val db = obtainLevelDB()
        db.putBytes(byteArrayOf(0x10), byteArrayOf(0x10))
        db.putBytes(byteArrayOf(0xC0.toByte()), byteArrayOf(0xC0.toByte()))
        db.putBytes(byteArrayOf(0xFF.toByte()), byteArrayOf(0xFF.toByte()))
        db.putBytes(byteArrayOf(0x80.toByte()), byteArrayOf(0x80.toByte()))

        db.iterator().use { it ->
            // Forward order is unsigned ascending: 0x10, 0x80, 0xC0, 0xFF.
            it.seekToFirst()
            assertTrue(it.isValid)
            assertContentEquals(byteArrayOf(0x10), it.key())
            it.next()
            assertContentEquals(byteArrayOf(0x80.toByte()), it.key())
            it.next()
            assertContentEquals(byteArrayOf(0xC0.toByte()), it.key())
            it.next()
            assertContentEquals(byteArrayOf(0xFF.toByte()), it.key())
            it.next()
            assertFalse(it.isValid)

            // seekToLast lands on 0xFF (the unsigned-largest), not 0x10.
            it.seekToLast()
            assertTrue(it.isValid)
            assertContentEquals(byteArrayOf(0xFF.toByte()), it.key())

            // Exact high-byte hit.
            it.seek(byteArrayOf(0x80.toByte()))
            assertTrue(it.isValid)
            assertContentEquals(byteArrayOf(0x80.toByte()), it.key())

            // Ceiling between 0x80 and 0xC0 -> 0xC0 (NOT floor 0x80).
            it.seek(byteArrayOf(0x90.toByte()))
            assertTrue(it.isValid)
            assertContentEquals(byteArrayOf(0xC0.toByte()), it.key())

            // Seek past the unsigned-largest key invalidates (0xFF is the max byte; nothing is greater).
            // A signed comparator would think keys "below" 0x10 still exist and could land somewhere.
            it.seek(byteArrayOf(0xFF.toByte()))
            assertContentEquals(byteArrayOf(0xFF.toByte()), it.key())
            it.next()
            assertFalse(it.isValid)
        }
        db.close()
    }

    /**
     * Iterator stability against a write()-induced store-reference SWAP. [testIteration] mutates via
     * putBytes/del (in-place) after the iterator is created; MockLevelDB.write() instead builds a NEW
     * SortedBytesStore copy and reassigns the `store` field. An iterator that captured a LIVE reference
     * to the store field (instead of a point-in-time snapshotCopy) would still pass [testIteration]
     * (in-place edits to the same object are visible to a snapshot-copy too, only if it weren't a copy)
     * but would DIVERGE here: after the swap it must NOT observe the newly-written keys.
     *
     * Pins: an iterator opened before write() observes the pre-write key set only.
     */
    @Test
    @Throws(Exception::class)
    fun testIteratorStableAcrossWriteStoreSwap() {
        val db = obtainLevelDB()
        db.putBytes(byteArrayOf(1), byteArrayOf(10))
        db.putBytes(byteArrayOf(2), byteArrayOf(20))

        val iterator = db.iterator()

        // write() swaps the underlying store to a fresh copy with extra/overwritten keys.
        val wb = SimpleWriteBatch()
        wb.put(byteArrayOf(3), byteArrayOf(30))   // new key
        wb.put(byteArrayOf(1), byteArrayOf(99))   // overwrite existing
        db.write(wb, true)

        // The pre-write iterator must still see ONLY {1->10, 2->20}: no key 3, and 1 still 10.
        val keys = ArrayList<ByteArray>()
        val values = ArrayList<ByteArray>()
        iterator.seekToFirst()
        while (iterator.isValid) {
            keys.add(iterator.key())
            values.add(iterator.value())
            iterator.next()
        }
        iterator.close()

        assertEquals(2, keys.size)
        assertContentEquals(byteArrayOf(1), keys[0])
        assertContentEquals(byteArrayOf(10), values[0])   // NOT 99
        assertContentEquals(byteArrayOf(2), keys[1])
        assertContentEquals(byteArrayOf(20), values[1])

        // A fresh iterator after the swap observes the new state.
        db.iterator().use { it ->
            it.seek(byteArrayOf(3))
            assertTrue(it.isValid)
            assertContentEquals(byteArrayOf(3), it.key())
            assertContentEquals(byteArrayOf(30), it.value())
        }

        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun testClosed() {
        val db = obtainLevelDB()
        val iterator = db.iterator(true)
        iterator.close()
        var threw = false
        try {
            iterator.isValid
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        assertTrue(threw)
        threw = false
        try {
            iterator.next()
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        assertTrue(threw)
        threw = false
        try {
            iterator.previous()
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        assertTrue(threw)
        threw = false
        try {
            iterator.key()
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        assertTrue(threw)
        threw = false
        try {
            iterator.value()
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        assertTrue(threw)
        db.close()
    }
}
