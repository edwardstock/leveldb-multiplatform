package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.open
import com.edwardstock.leveldb.common.SnapshotTest
import com.edwardstock.leveldb.exception.LevelDBSnapshotOwnershipException
import kotlin.test.Test
import kotlin.test.assertFailsWith
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

    @Throws(Exception::class)
    override fun obtainLevelDB(): LevelDB {
        return LevelDB.open(dbFile)
    }
}
