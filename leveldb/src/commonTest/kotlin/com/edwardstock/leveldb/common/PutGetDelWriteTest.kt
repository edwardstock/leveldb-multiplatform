package com.edwardstock.leveldb.common

import com.edwardstock.leveldb.api.get
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.impl.SimpleWriteBatch
import com.edwardstock.leveldb.internal.BytesHelper.lexicographicCompare
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
 */ abstract class PutGetDelWriteTest : DatabaseTestCase() {
    @Test
    @Throws(Exception::class)
    fun testPut() {
        val db = obtainLevelDB()
        db.putBytes(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), true)
        db.putBytes(byteArrayOf(1, 2, 3, 4), byteArrayOf(1, 2, 3, 4), false)
        db.putBytes(byteArrayOf(1, 2, 3, 4, 5), null, false)

        db.close()
        assertFailsWith<LevelDBClosedException> {
            db.putBytes(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), false)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testGet() {
        val db = obtainLevelDB()
        db.putBytes(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), false)
        var result = db[byteArrayOf(1, 2, 3)]
        assertNotNull(result)
        assertEquals(0, lexicographicCompare(byteArrayOf(1, 2, 3), result).toLong())

        db.putBytes(byteArrayOf(1, 2, 4), byteArrayOf(1, 2, 4), true)
        result = db[byteArrayOf(1, 2, 4)]
        assertNotNull(result)
        assertEquals(0, lexicographicCompare(byteArrayOf(1, 2, 4), result).toLong())

        db.putBytes(byteArrayOf(1, 2, 4), null, false)
        result = db[byteArrayOf(1, 2, 4)]
        assertNull(result)

        db.close()

        assertFailsWith<LevelDBClosedException> {
            db[byteArrayOf(1, 2, 3)]
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDel() {
        val db = obtainLevelDB()
        db.putBytes(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), false)
        assertNotNull(db[byteArrayOf(1, 2, 3)])
        db.del(byteArrayOf(1, 2, 3), false)
        assertNull(db[byteArrayOf(1, 2, 3)])
        db.putBytes(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), false)
        assertNotNull(db[byteArrayOf(1, 2, 3)])
        db.del(byteArrayOf(1, 2, 3), true)
        assertNull(db[byteArrayOf(1, 2, 3)])

        db.close()

        assertFailsWith<LevelDBClosedException> {
            db.del(byteArrayOf(1, 2, 3), false)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testWrite() {
        val db = obtainLevelDB()
        var swb = SimpleWriteBatch()
        swb.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3))
        swb.put(byteArrayOf(1, 2, 3, 4), byteArrayOf(1, 2, 3, 4))
        db.write(swb, false)

        assertNotNull(db[byteArrayOf(1, 2, 3)])
        assertNotNull(db[byteArrayOf(1, 2, 3, 4)])


        swb = SimpleWriteBatch()
        swb.put(byteArrayOf(1, 2, 3, 4), byteArrayOf(1, 2, 3))
        swb.del(byteArrayOf(1, 2, 3))
        db.write(swb, true)


        assertNotNull(db[byteArrayOf(1, 2, 3, 4)])
        assertEquals(
            0,
            lexicographicCompare(db[byteArrayOf(1, 2, 3, 4)], byteArrayOf(1, 2, 3)).toLong()
        )
        assertNull(db[byteArrayOf(1, 2, 3)])

        db.close()

        assertFailsWith<LevelDBClosedException> {
            db.write(SimpleWriteBatch(), false)
        }
    }

    /**
     * Last-write-wins for a key that appears MORE THAN ONCE in a single [WriteBatch]. The shared
     * [testWrite] only puts distinct keys, so the within-batch ordering contract is otherwise
     * unpinned. Native applies batch operations in insertion order, so the LAST operation on a key
     * wins. This pins:
     *   - put A=1 then put A=2 in one batch -> A == 2,
     *   - put B=1 then del B in one batch -> B absent,
     *   - del C then put C=9 in one batch -> C == 9.
     *
     * Catches a Mock divergence where write() reversed the apply order of two ops on the same key,
     * or where SimpleWriteBatch coalesced/reordered duplicates differently from native.
     */
    @Test
    @Throws(Exception::class)
    fun testWriteBatchDuplicateKeyLastWriteWins() {
        val db = obtainLevelDB()

        val a = byteArrayOf(1)
        val b = byteArrayOf(2)
        val c = byteArrayOf(3)
        // Seed C so the "del then put" sequence is meaningful.
        db.putBytes(c, byteArrayOf(0))

        val swb = SimpleWriteBatch()
        swb.put(a, byteArrayOf(1))
        swb.put(a, byteArrayOf(2))   // overwrites the earlier put -> A must be 2
        swb.put(b, byteArrayOf(1))
        swb.del(b)                   // delete after put -> B must be absent
        swb.del(c)
        swb.put(c, byteArrayOf(9))   // put after delete -> C must be 9
        db.write(swb, false)

        assertContentEquals(byteArrayOf(2), db[a])
        assertNull(db[b])
        assertContentEquals(byteArrayOf(9), db[c])

        db.close()
    }

    /**
     * Empty key and empty (zero-length, non-null) value are both first-class in native LevelDB:
     *   - an empty key is a valid, retrievable key,
     *   - an empty value is distinct from a missing key (put(k, ByteArray(0)) is a PUT, not a delete;
     *     only a null value deletes).
     *
     * No other shared put/get test exercises these on the live DB. Catches a Mock divergence where
     * putBytes(key, ByteArray(0)) was conflated with delete (value.isEmpty() treated like value==null),
     * or where the empty key was special-cased/rejected by SortedBytesStore.
     */
    @Test
    @Throws(Exception::class)
    fun testEmptyKeyAndEmptyValueAreDistinctFromMissing() {
        val db = obtainLevelDB()

        val emptyKey = byteArrayOf()
        val key = byteArrayOf(7)

        db.putBytes(emptyKey, byteArrayOf(1))
        db.putBytes(key, byteArrayOf())   // zero-length value: a real PUT, NOT a delete

        // Empty key round-trips.
        assertContentEquals(byteArrayOf(1), db[emptyKey])

        // Empty value is present and non-null (distinct from a missing key).
        val emptyValue = db[key]
        assertNotNull(emptyValue)
        assertContentEquals(byteArrayOf(), emptyValue)

        // A genuinely missing key is null — proving the above non-null is not a coincidence.
        assertNull(db[byteArrayOf(99)])

        // null value really does delete the empty-value key.
        db.putBytes(key, null)
        assertNull(db[key])

        db.close()
    }

    /**
     * Persistence / multi-open parity: data written through one instance of the path must be visible
     * to a SECOND instance opened on the SAME path after the first is closed. Native persists to the
     * on-disk LevelDB; the Mock persists via its per-path store registry. This is the literal cross-open
     * parity T2 names ("multiple opens of the same path share the same data") and is otherwise
     * unexercised by any shared suite.
     *
     * Catches a Mock divergence where close() wiped the registry entry, or where putBytes/del mutated
     * the store without persisting it back to the registry for the path.
     */
    @Test
    @Throws(Exception::class)
    fun testReopenSamePathSeesPersistedData() {
        val keptKey = byteArrayOf(1, 1, 1)
        val deletedKey = byteArrayOf(2, 2, 2)

        val first = obtainLevelDB()
        first.putBytes(keptKey, byteArrayOf(42))
        first.putBytes(deletedKey, byteArrayOf(7))
        first.del(deletedKey)
        first.close()

        // Second instance over the SAME dbFile path.
        val second = obtainLevelDB()
        assertContentEquals(byteArrayOf(42), second[keptKey])
        assertNull(second[deletedKey])
        second.close()
    }

    /**
     * UNSIGNED byte ordering parity (the literal 'key order' parity target of T2). Native LevelDB's
     * BytewiseComparator orders bytes as UNSIGNED, so a key beginning with a high byte (>= 0x80) sorts
     * AFTER a low-byte key, and 0xFF sorts last. Every other shared put/get/iteration test uses only
     * bytes < 0x80, where signed and unsigned comparison agree, so a sign bug is invisible to them.
     *
     * This stores keys spanning the 0x7F/0x80 boundary and the 0xFF top, then walks them in stored
     * order via a live iterator. The required order is the UNSIGNED order: 0x00, 0x7F, 0x80, 0xFF.
     *
     * Catches the exact production mutation called out in review: dropping `and 0xFF` in
     * BytesHelper.lexicographicCompare (signed byte compare). Under signed comparison 0x80 and 0xFF
     * are negative and would sort BEFORE 0x00/0x7F, flipping the order and failing the assertions.
     * Because Mock and Native both route key ordering through this single comparator
     * (LevelDbByteArrayComparator -> BytesHelper.lexicographicCompare), the test pins both drivers
     * to identical unsigned semantics.
     */
    @Test
    @Throws(Exception::class)
    fun testHighByteKeysOrderUnsigned() {
        val db = obtainLevelDB()

        val k00 = byteArrayOf(0x00)
        val k7f = byteArrayOf(0x7F)
        val k80 = byteArrayOf(0x80.toByte())
        val kff = byteArrayOf(0xFF.toByte())

        // Insert deliberately out of order.
        db.putBytes(k80, byteArrayOf(0x80.toByte()))
        db.putBytes(k00, byteArrayOf(0x00))
        db.putBytes(kff, byteArrayOf(0xFF.toByte()))
        db.putBytes(k7f, byteArrayOf(0x7F))

        val orderedKeys = ArrayList<ByteArray>()
        db.iterator().use { it ->
            it.seekToFirst()
            while (it.isValid) {
                orderedKeys.add(it.key())
                it.next()
            }
        }

        assertEquals(4, orderedKeys.size)
        // Unsigned ascending order. Under SIGNED comparison this would be 0x80, 0xFF, 0x00, 0x7F.
        assertContentEquals(k00, orderedKeys[0])
        assertContentEquals(k7f, orderedKeys[1])
        assertContentEquals(k80, orderedKeys[2])
        assertContentEquals(kff, orderedKeys[3])

        // Two-byte tiebreak: high byte in the FIRST position dominates. {0x80,0x00} must sort AFTER
        // {0x7F,0xFF} under unsigned order (0x80 > 0x7F), but BEFORE under signed (0x80 is negative).
        db.putBytes(byteArrayOf(0x80.toByte(), 0x00), byteArrayOf(1))
        db.putBytes(byteArrayOf(0x7F, 0xFF.toByte()), byteArrayOf(2))
        assertEquals(
            1,
            lexicographicCompare(
                byteArrayOf(0x80.toByte(), 0x00),
                byteArrayOf(0x7F, 0xFF.toByte())
            ).coerceIn(-1, 1).toLong()
        )

        db.close()
    }

    /**
     * A zero-length (non-null) value PUT is a real PUT, NOT a delete, EVEN when the key's bytes are
     * all >= 0x80. The shared [testEmptyKeyAndEmptyValueAreDistinctFromMissing] only uses key byte 7
     * (< 0x80), so a Mock mutation that dropped empty-value puts specifically for high-byte keys
     * (e.g. `if (value.isEmpty() && key.all { it < 0 }) return`) would pass there but fail here.
     */
    @Test
    @Throws(Exception::class)
    fun testEmptyValueOnHighByteKeyIsStored() {
        val db = obtainLevelDB()

        val highKey = byteArrayOf(0x80.toByte(), 0xFF.toByte())
        db.putBytes(highKey, byteArrayOf())

        val v = db[highKey]
        assertNotNull(v)
        assertContentEquals(byteArrayOf(), v)

        // Distinct from a genuinely missing high-byte key.
        assertNull(db[byteArrayOf(0x90.toByte())])

        db.close()
    }

    /**
     * Deleting a key that was never written must be a silent no-op (not throw, not corrupt the store),
     * for both [LevelDB.del] and a delete operation inside a [WriteBatch]. The shared [testDel] only
     * deletes keys it just inserted, so del-of-absent is otherwise unpinned. Native treats a delete of
     * a missing key as a no-op; this forces the Mock to match.
     *
     * Catches a Mock divergence where del()/write-del of an absent key threw (e.g. an index lookup
     * that asserted presence) or removed a neighboring key.
     */
    @Test
    @Throws(Exception::class)
    fun testDeleteNonExistentKeyIsSilentNoOp() {
        val db = obtainLevelDB()

        val present = byteArrayOf(5)
        val absent = byteArrayOf(6)
        db.putBytes(present, byteArrayOf(50))

        // Direct del of an absent key: no throw, neighbor untouched.
        db.del(absent)
        assertNull(db[absent])
        assertContentEquals(byteArrayOf(50), db[present])

        // Batch del of an absent key alongside a real put: no throw, both effects correct.
        val swb = SimpleWriteBatch()
        swb.del(byteArrayOf(7))             // never existed
        swb.put(byteArrayOf(8), byteArrayOf(80))
        db.write(swb, false)

        assertNull(db[byteArrayOf(7)])
        assertContentEquals(byteArrayOf(80), db[byteArrayOf(8)])
        assertContentEquals(byteArrayOf(50), db[present])

        db.close()
    }
}
