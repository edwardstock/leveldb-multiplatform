package com.edwardstock.leveldb.common

import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.assertEquals
import kotlin.test.Test
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
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OFz SUBSTITUTE GOODS OR SERVICES;
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
        db.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3))
        db.put(byteArrayOf(3, 4, 5), byteArrayOf(3, 4, 5))
        val snapshotA = db.obtainSnapshot()

        db.put(byteArrayOf(5, 6, 7), byteArrayOf(5, 6, 7))
        assertNotNull(db[byteArrayOf(5, 6, 7)])

        val snapshotB = db.obtainSnapshot()
        var value = db[byteArrayOf(1, 2, 3), snapshotA]
        assertEquals(value, byteArrayOf(1, 2, 3))

        value = db[byteArrayOf(1, 2, 3), snapshotB]
        assertEquals(value, byteArrayOf(1, 2, 3))

        value = db[byteArrayOf(3, 4, 5), snapshotA]
        assertEquals(value, byteArrayOf(3, 4, 5))

        value = db[byteArrayOf(3, 4, 5), snapshotB]
        assertEquals(value, byteArrayOf(3, 4, 5))

        value = db[byteArrayOf(5, 6, 7), snapshotA]
        assertNull(value)

        value = db[byteArrayOf(5, 6, 7), snapshotB]
        assertEquals(value, byteArrayOf(5, 6, 7))

        db.close()
    }

    @Throws(Exception::class)
    fun testIteration() {
    }
}
