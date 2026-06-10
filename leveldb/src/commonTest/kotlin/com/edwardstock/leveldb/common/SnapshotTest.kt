package com.edwardstock.leveldb.common

import com.edwardstock.leveldb.api.get
import com.edwardstock.leveldb.api.iterator
import com.edwardstock.leveldb.exception.LevelDBClosedException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
abstract class SnapshotTest : DatabaseTestCase() {
    @Test
    @Throws(Exception::class)
    fun testObtainReleaseSnapshot() {
        val db = obtainLevelDB()

        var snapshot = db.obtainSnapshot()
        assertNotNull(snapshot)
        assertFalse(snapshot.isReleased)

        snapshot.close()
        assertTrue(snapshot.isReleased)

        snapshot = db.obtainSnapshot()
        assertNotNull(snapshot)
        assertFalse(snapshot.isReleased)
        snapshot.close()

        db.close()

        assertFailsWith<LevelDBClosedException> {
            db.obtainSnapshot()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testGet() {
        val db = obtainLevelDB()
        db.putBytes(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3))
        db.putBytes(byteArrayOf(3, 4, 5), byteArrayOf(3, 4, 5))
        val snapshotA = db.obtainSnapshot()

        db.putBytes(byteArrayOf(5, 6, 7), byteArrayOf(5, 6, 7))
        assertNotNull(db[byteArrayOf(5, 6, 7)])

        val snapshotB = db.obtainSnapshot()
        var value = db[byteArrayOf(1, 2, 3), snapshotA]
        assertContentEquals(byteArrayOf(1, 2, 3), value)

        value = db[byteArrayOf(1, 2, 3), snapshotB]
        assertContentEquals(byteArrayOf(1, 2, 3), value)

        value = db[byteArrayOf(3, 4, 5), snapshotA]
        assertContentEquals(byteArrayOf(3, 4, 5), value)

        value = db[byteArrayOf(3, 4, 5), snapshotB]
        assertContentEquals(byteArrayOf(3, 4, 5), value)

        // key (5,6,7) was inserted AFTER snapshotA was taken, so the
        // point-in-time view from snapshotA must NOT see it.
        value = db[byteArrayOf(5, 6, 7), snapshotA]
        assertNull(value)

        // snapshotB was taken after the insert, so it sees the value.
        value = db[byteArrayOf(5, 6, 7), snapshotB]
        assertContentEquals(byteArrayOf(5, 6, 7), value)

        snapshotA.close()
        snapshotB.close()
        db.close()
    }

    /**
     * Symmetric to [testGet]: a point get through a snapshot taken BEFORE a key was deleted
     * (or overwritten) on the live DB must still observe the OLD value, not the live mutation.
     *
     * This pins the read-path of the snapshot for mutations that happen after the snapshot is
     * taken — the case [testGet] never covers (it only proves insert-after-snapshot is invisible).
     */
    @Test
    @Throws(Exception::class)
    fun testGetSeesPreMutationStateAfterDeleteAndOverwrite() {
        val db = obtainLevelDB()

        val keyDel = byteArrayOf(10, 11, 12)
        val keyOver = byteArrayOf(20, 21, 22)
        db.putBytes(keyDel, byteArrayOf(100))
        db.putBytes(keyOver, byteArrayOf(1))

        val snapshot = db.obtainSnapshot()

        // Mutate the LIVE db after the snapshot:
        //   - delete keyDel
        //   - overwrite keyOver -> 2
        db.del(keyDel)
        db.putBytes(keyOver, byteArrayOf(2))

        // Live reads observe the mutations.
        assertNull(db[keyDel])
        assertContentEquals(byteArrayOf(2), db[keyOver])

        // Snapshot reads must still observe the ORIGINAL state.
        // If getBytes ignored the snapshot for deletions and fell back to the live DB,
        // this would return null and fail.
        assertContentEquals(byteArrayOf(100), db[keyDel, snapshot])
        // If getBytes ignored the snapshot for overwrites, this would return 2 and fail.
        assertContentEquals(byteArrayOf(1), db[keyOver, snapshot])

        snapshot.close()
        db.close()
    }

    /**
     * LevelDB distinguishes an empty value from a missing key, and an empty key is a valid key.
     * Pin both round-trip through a snapshot point get.
     */
    @Test
    @Throws(Exception::class)
    fun testGetEmptyKeyAndEmptyValueUnderSnapshot() {
        val db = obtainLevelDB()

        val emptyKey = byteArrayOf()
        val nonEmptyKeyEmptyValue = byteArrayOf(42)
        db.putBytes(emptyKey, byteArrayOf(7))
        db.putBytes(nonEmptyKeyEmptyValue, byteArrayOf())

        val snapshot = db.obtainSnapshot()

        // After the snapshot, change both on the live DB so the snapshot read cannot
        // accidentally fall through to live state and still pass.
        db.putBytes(emptyKey, byteArrayOf(8))
        db.del(nonEmptyKeyEmptyValue)

        // Empty key is a real key: snapshot must see its original value.
        assertContentEquals(byteArrayOf(7), db[emptyKey, snapshot])

        // Empty value is distinct from "missing": snapshot must return an empty (non-null)
        // ByteArray, not null. A mutation conflating empty-value with not-found would fail here.
        val emptyValue = db[nonEmptyKeyEmptyValue, snapshot]
        assertNotNull(emptyValue)
        assertContentEquals(byteArrayOf(), emptyValue)

        // And on the live DB the empty-value key is now actually deleted.
        assertNull(db[nonEmptyKeyEmptyValue])

        snapshot.close()
        db.close()
    }

    /**
     * Reading through a RELEASED snapshot must fall back to LIVE state — it must NOT keep serving
     * the point-in-time view captured before release. This is the native contract (a released
     * snapshot's handle is null, so the get/iterator path reads the live DB) and the cross-driver
     * parity T2 forces.
     *
     * Catches a Mock divergence where releasing a snapshot has no read-side effect: getBytes(key,
     * snapshot) / iterator(snapshot) still read from the captured MockSnapshot.store after close(),
     * so the released snapshot keeps returning stale point-in-time data instead of live data. With
     * the release ignored, the live-state assertions below fail.
     */
    @Test
    @Throws(Exception::class)
    fun testReadsThroughReleasedSnapshotSeeLiveState() {
        val db = obtainLevelDB()
        val key = byteArrayOf(1, 2, 3)
        db.putBytes(key, byteArrayOf(1))

        val snapshot = db.obtainSnapshot()
        // Mutate AFTER the snapshot, then release it.
        db.putBytes(key, byteArrayOf(2))
        val newKey = byteArrayOf(9)
        db.putBytes(newKey, byteArrayOf(9))
        snapshot.close()
        assertTrue(snapshot.isReleased)

        // Point gets through the released snapshot observe LIVE state, not the captured view.
        // If release were ignored, the snapshot would still serve key->1 and miss newKey, failing here.
        assertContentEquals(byteArrayOf(2), db[key, snapshot])
        assertContentEquals(byteArrayOf(9), db[newKey, snapshot])

        // A key deleted on the live DB reads as missing through the released snapshot.
        db.del(key)
        assertNull(db[key, snapshot])

        // An iterator opened with the released snapshot reflects the live DB (only newKey remains).
        db.iterator(snapshot).use { it ->
            it.seekToFirst()
            assertTrue(it.isValid)
            assertContentEquals(newKey, it.key())
            assertContentEquals(byteArrayOf(9), it.value())
            it.next()
            assertFalse(it.isValid)
        }

        db.close()
    }

    /**
     * An iterator bound to a snapshot must observe the database as it was when the snapshot was
     * taken: inserts, updates and deletes performed after the snapshot must be invisible to it,
     * while a default (live) iterator must observe the current state.
     */
    @Test
    @Throws(Exception::class)
    fun testIteration() {
        val db = obtainLevelDB()

        // Initial state captured by the snapshot:
        //   a -> 1, b -> 2, c -> 3
        db.putBytes(byteArrayOf('a'.code.toByte()), byteArrayOf(1))
        db.putBytes(byteArrayOf('b'.code.toByte()), byteArrayOf(2))
        db.putBytes(byteArrayOf('c'.code.toByte()), byteArrayOf(3))

        val snapshot = db.obtainSnapshot()

        // Mutate the live database AFTER the snapshot was taken:
        //   - insert a new key (d)
        //   - overwrite an existing value (b -> 20)
        //   - delete an existing key (a)
        db.putBytes(byteArrayOf('d'.code.toByte()), byteArrayOf(4))
        db.putBytes(byteArrayOf('b'.code.toByte()), byteArrayOf(20))
        db.del(byteArrayOf('a'.code.toByte()))

        // Read the snapshot iterator: it must still see the ORIGINAL state.
        val snapKeys = ArrayList<ByteArray>()
        val snapValues = ArrayList<ByteArray>()
        db.iterator(snapshot).use { it ->
            it.seekToFirst()
            while (it.isValid) {
                snapKeys.add(it.key())
                snapValues.add(it.value())
                it.next()
            }
        }

        assertEquals(3, snapKeys.size)
        assertContentEquals(byteArrayOf('a'.code.toByte()), snapKeys[0])
        assertContentEquals(byteArrayOf(1), snapValues[0])
        assertContentEquals(byteArrayOf('b'.code.toByte()), snapKeys[1])
        // The snapshot must NOT see the overwrite (b is still 2, not 20).
        assertContentEquals(byteArrayOf(2), snapValues[1])
        assertContentEquals(byteArrayOf('c'.code.toByte()), snapKeys[2])
        assertContentEquals(byteArrayOf(3), snapValues[2])

        // REVERSE iteration under the same snapshot must also honor the point-in-time view.
        // A mutation that broke seekToLast()/previous() while keeping forward iteration correct
        // would slip past the forward walk above; this catches it.
        val reverseKeys = ArrayList<ByteArray>()
        val reverseValues = ArrayList<ByteArray>()
        db.iterator(snapshot).use { it ->
            it.seekToLast()
            while (it.isValid) {
                reverseKeys.add(it.key())
                reverseValues.add(it.value())
                it.previous()
            }
        }

        assertEquals(3, reverseKeys.size)
        // Reverse order: c, b(original 2), a(original 1) — deleted 'a' is still present,
        // overwritten 'b' is still 2, inserted 'd' is absent.
        assertContentEquals(byteArrayOf('c'.code.toByte()), reverseKeys[0])
        assertContentEquals(byteArrayOf(3), reverseValues[0])
        assertContentEquals(byteArrayOf('b'.code.toByte()), reverseKeys[1])
        assertContentEquals(byteArrayOf(2), reverseValues[1])
        assertContentEquals(byteArrayOf('a'.code.toByte()), reverseKeys[2])
        assertContentEquals(byteArrayOf(1), reverseValues[2])

        // seek(key) for a key inserted AFTER the snapshot ('d') must NOT land on it; since 'd'
        // is the largest key and does not exist in the snapshot view, the seek runs off the end
        // and the iterator is invalid. A mutation that let the snapshot iterator see post-snapshot
        // inserts would make this valid and land on 'd' -> 4.
        db.iterator(snapshot).use { it ->
            it.seek(byteArrayOf('d'.code.toByte()))
            assertFalse(it.isValid)
        }

        // seek(key) to a key that DOES exist in the snapshot view but was deleted on the live DB
        // ('a') must still land on it with the original value.
        db.iterator(snapshot).use { it ->
            it.seek(byteArrayOf('a'.code.toByte()))
            assertTrue(it.isValid)
            assertContentEquals(byteArrayOf('a'.code.toByte()), it.key())
            assertContentEquals(byteArrayOf(1), it.value())
        }

        // Read the live iterator: it must see the CURRENT state:
        //   b -> 20, c -> 3, d -> 4 (a deleted, b overwritten, d inserted).
        val liveKeys = ArrayList<ByteArray>()
        val liveValues = ArrayList<ByteArray>()
        db.iterator().use { it ->
            it.seekToFirst()
            while (it.isValid) {
                liveKeys.add(it.key())
                liveValues.add(it.value())
                it.next()
            }
        }

        assertEquals(3, liveKeys.size)
        assertContentEquals(byteArrayOf('b'.code.toByte()), liveKeys[0])
        assertContentEquals(byteArrayOf(20), liveValues[0])
        assertContentEquals(byteArrayOf('c'.code.toByte()), liveKeys[1])
        assertContentEquals(byteArrayOf(3), liveValues[1])
        assertContentEquals(byteArrayOf('d'.code.toByte()), liveKeys[2])
        assertContentEquals(byteArrayOf(4), liveValues[2])

        snapshot.close()
        db.close()
    }

    /**
     * A snapshot iterator must order keys by the SAME unsigned byte ordering as the native
     * BytewiseComparator, including keys whose first byte is >= 0x80. Every other shared snapshot
     * test uses bytes < 0x80, where signed and unsigned comparison agree, so a sign bug in the
     * shared comparator is invisible to them even though both drivers route through it.
     *
     * After taking the snapshot, the live DB is mutated (a high-byte key deleted, another inserted)
     * so the snapshot iterator cannot accidentally fall through to live state and still pass. The
     * snapshot must yield exactly the captured high-byte keys in unsigned order: 0x01, 0x80, 0xFF.
     *
     * Catches the signed-vs-unsigned comparator mutation (drop of `and 0xFF`): under signed compare
     * 0x80 and 0xFF sort before 0x01, flipping the snapshot iteration order and failing here. Also
     * catches a snapshot iterator that observed post-snapshot mutations on high-byte keys.
     */
    @Test
    @Throws(Exception::class)
    fun testSnapshotIterationHighByteUnsignedOrder() {
        val db = obtainLevelDB()

        val k01 = byteArrayOf(0x01)
        val k80 = byteArrayOf(0x80.toByte())
        val kff = byteArrayOf(0xFF.toByte())
        db.putBytes(k80, byteArrayOf(0x80.toByte()))
        db.putBytes(kff, byteArrayOf(0xFF.toByte()))
        db.putBytes(k01, byteArrayOf(0x01))

        val snapshot = db.obtainSnapshot()

        // Mutate live AFTER the snapshot: delete a captured high-byte key, insert a new one.
        db.del(k80)
        db.putBytes(byteArrayOf(0xC0.toByte()), byteArrayOf(0xC0.toByte()))

        val snapKeys = ArrayList<ByteArray>()
        db.iterator(snapshot).use { it ->
            it.seekToFirst()
            while (it.isValid) {
                snapKeys.add(it.key())
                it.next()
            }
        }

        // Unsigned ascending, captured-at-snapshot set: 0x01, 0x80, 0xFF.
        // (0x80 still present in the snapshot despite the live delete; 0xC0 absent, inserted later.)
        assertEquals(3, snapKeys.size)
        assertContentEquals(k01, snapKeys[0])
        assertContentEquals(k80, snapKeys[1])
        assertContentEquals(kff, snapKeys[2])

        snapshot.close()
        db.close()
    }
}
