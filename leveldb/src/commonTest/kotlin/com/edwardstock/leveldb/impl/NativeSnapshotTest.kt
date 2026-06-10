package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.get
import com.edwardstock.leveldb.api.iterator
import com.edwardstock.leveldb.api.open
import com.edwardstock.leveldb.common.SnapshotTest
import com.edwardstock.leveldb.exception.LevelDBSnapshotOwnershipException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
class NativeSnapshotTest : SnapshotTest() {
    @Test
    fun testCloseReleasesSnapshots() {
        val db = obtainLevelDB()
        val a = db.obtainSnapshot()
        val b = db.obtainSnapshot()

        db.close()

        assertTrue(a.isReleased)
        assertTrue(b.isReleased)
    }

    @Test
    fun testIteratorRejectsForeignSnapshot() {
        val dbA = obtainLevelDB()
        val dbB = LevelDB.open(createRandomDbPath())
        val snapshot = dbA.obtainSnapshot()
        try {
            assertFailsWith<LevelDBSnapshotOwnershipException> {
                dbB.iterator(fillCache = true, snapshot = snapshot)
            }
        } finally {
            snapshot.close()
            dbA.close()
            dbB.close()
        }
    }

    /**
     * Cross-DB ownership for the GET path (NativeSnapshot.checkOwner / ownerToken).
     * A snapshot obtained from dbA must be rejected by dbB's point get. If the ownerToken
     * guard were removed (or checkOwner always returned true), this would silently read
     * dbB's live state instead of throwing.
     */
    @Test
    fun testGetRejectsForeignSnapshot() {
        val dbA = obtainLevelDB()
        val dbB = LevelDB.open(createRandomDbPath())
        val snapshot = dbA.obtainSnapshot()
        try {
            assertFailsWith<LevelDBSnapshotOwnershipException> {
                dbB[byteArrayOf(1), snapshot]
            }
        } finally {
            snapshot.close()
            dbA.close()
            dbB.close()
        }
    }

    /**
     * Native contract for reading through a RELEASED snapshot: once closed, the snapshot's
     * native handle is null, so reads fall back to the LIVE database (they do NOT throw and do
     * NOT keep serving the point-in-time view). This pins that fall-back so a mutation that, say,
     * kept serving stale snapshot data — or crashed — after release would be caught.
     */
    @Test
    fun testGetThroughReleasedSnapshotReadsLiveState() {
        val db = obtainLevelDB()
        val key = byteArrayOf(1, 2, 3)
        db.putBytes(key, byteArrayOf(1))

        val snapshot = db.obtainSnapshot()
        // Mutate after snapshot, then release the snapshot.
        db.putBytes(key, byteArrayOf(2))
        val newKey = byteArrayOf(9)
        db.putBytes(newKey, byteArrayOf(9))
        snapshot.close()
        assertTrue(snapshot.isReleased)

        // Reading through the released snapshot now observes LIVE state, not the point-in-time view.
        assertContentEquals(byteArrayOf(2), db[key, snapshot])
        assertContentEquals(byteArrayOf(9), db[newKey, snapshot])

        // And a key deleted on the live DB reads as missing through the released snapshot.
        db.del(key)
        assertNull(db[key, snapshot])

        db.iterator(snapshot).use { it ->
            it.seekToFirst()
            assertTrue(it.isValid)
            // Live state now has only newKey (key was deleted); the released snapshot iterator
            // reflects the live DB, not the original captured view.
            assertContentEquals(newKey, it.key())
            assertContentEquals(byteArrayOf(9), it.value())
            it.next()
            assertFalse(it.isValid)
        }

        db.close()
    }

    @Throws(Exception::class)
    override fun obtainLevelDB(): LevelDB {
        return LevelDB.open(dbFile)
    }
}
