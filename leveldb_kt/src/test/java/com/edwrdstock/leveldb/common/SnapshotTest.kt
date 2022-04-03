package com.edwrdstock.leveldb.common

import com.edwardstock.leveldb.Bytes.lexicographicCompare
import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBSnapshotOwnershipException
import com.edwardstock.leveldb.implementation.mock.MockLevelDB
import com.edwardstock.leveldb.implementation.mock.MockSnapshot
import org.junit.Assert
import org.junit.Test

/*
 * Stojan Dimitrovski
 *
 * Copyright (c) 2014, Stojan Dimitrovski <sdimitrovski@gmail.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
abstract class SnapshotTest : DatabaseTestCase() {
    @Test
    @Throws(Exception::class)
    fun testObtainReleaseSnapshot() {
        var db: LevelDB? = obtainLevelDB()
        var snapshot = db!!.obtainSnapshot()
        Assert.assertNotNull(snapshot)
        Assert.assertFalse(snapshot!!.isReleased)
        db.releaseSnapshot(snapshot)
        Assert.assertTrue(snapshot.isReleased)
        snapshot = db.obtainSnapshot()
        Assert.assertNotNull(snapshot)
        Assert.assertFalse(snapshot!!.isReleased)
        db.releaseSnapshot(snapshot)
        var threw = false
        try {
            db.releaseSnapshot(null)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        Assert.assertTrue(threw)
        threw = false
        try {
            db.releaseSnapshot(MockSnapshot(MockLevelDB()))
        } catch (e: LevelDBSnapshotOwnershipException) {
            threw = true
        }
        Assert.assertTrue(threw)
        db.close()
        threw = false
        try {
            db.releaseSnapshot(snapshot)
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        Assert.assertTrue(threw)
        Assert.assertTrue(snapshot.isReleased)
        System.gc()
        Assert.assertTrue(snapshot.isReleased)
    }

    @Test
    @Throws(Exception::class)
    fun testGet() {
        val db = obtainLevelDB()
        db.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3))
        db.put(byteArrayOf(3, 4, 5), byteArrayOf(3, 4, 5))
        val snapshotA = db.obtainSnapshot()
        db.put(byteArrayOf(5, 6, 7), byteArrayOf(5, 6, 7))
        Assert.assertNotNull(db[byteArrayOf(5, 6, 7)])
        val snapshotB = db.obtainSnapshot()
        var value = db[byteArrayOf(1, 2, 3), snapshotA]
        Assert.assertNotNull(value)
        Assert.assertEquals(0, lexicographicCompare(value, byteArrayOf(1, 2, 3)).toLong())
        value = db[byteArrayOf(1, 2, 3), snapshotB]
        Assert.assertNotNull(value)
        Assert.assertEquals(0, lexicographicCompare(value, byteArrayOf(1, 2, 3)).toLong())
        value = db[byteArrayOf(3, 4, 5), snapshotA]
        Assert.assertNotNull(value)
        Assert.assertEquals(0, lexicographicCompare(value, byteArrayOf(3, 4, 5)).toLong())
        value = db[byteArrayOf(3, 4, 5), snapshotB]
        Assert.assertNotNull(value)
        Assert.assertEquals(0, lexicographicCompare(value, byteArrayOf(3, 4, 5)).toLong())
        value = db[byteArrayOf(5, 6, 7), snapshotA]
        Assert.assertNull(value)
        value = db[byteArrayOf(5, 6, 7), snapshotB]
        Assert.assertNotNull(value)
        Assert.assertEquals(0, lexicographicCompare(value, byteArrayOf(5, 6, 7)).toLong())
        db.releaseSnapshot(snapshotA)
        db.releaseSnapshot(snapshotB)
        db.close()
    }

    @Throws(Exception::class)
    fun testIteration() {
    }
}
