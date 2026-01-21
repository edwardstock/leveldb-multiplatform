package com.edwardstock.leveldb.common

import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.implementation.SimpleWriteBatch
import com.edwardstock.leveldb.utils.BytesHelper.lexicographicCompare
import kotlin.test.Test
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
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OFz SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 */ abstract class PutGetDelWriteTest : DatabaseTestCase() {
    @Test
    @Throws(Exception::class)
    fun testPut() {
        val db = obtainLevelDB()
        db.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), true)
        db.put(byteArrayOf(1, 2, 3, 4), byteArrayOf(1, 2, 3, 4), false)
        db.put(byteArrayOf(1, 2, 3, 4, 5), null, false)

        db.close()
        assertFailsWith<LevelDBClosedException> {
            db.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), false)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testGet() {
        val db = obtainLevelDB()
        db.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), false)
        var result = db[byteArrayOf(1, 2, 3)]
        assertNotNull(result)
        assertEquals(0, lexicographicCompare(byteArrayOf(1, 2, 3), result).toLong())

        db.put(byteArrayOf(1, 2, 4), byteArrayOf(1, 2, 4), true)
        result = db[byteArrayOf(1, 2, 4)]
        assertNotNull(result)
        assertEquals(0, lexicographicCompare(byteArrayOf(1, 2, 4), result).toLong())

        db.put(byteArrayOf(1, 2, 4), null, false)
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
        db.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), false)
        assertNotNull(db[byteArrayOf(1, 2, 3)])
        db.del(byteArrayOf(1, 2, 3), false)
        assertNull(db[byteArrayOf(1, 2, 3)])
        db.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), false)
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
}
