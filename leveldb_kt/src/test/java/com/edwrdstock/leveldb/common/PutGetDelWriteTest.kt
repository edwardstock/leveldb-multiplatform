package com.edwrdstock.leveldb.common

import com.edwardstock.leveldb.Bytes.lexicographicCompare
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.implementation.SimpleWriteBatch
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
 */ /**
 * Created by hermann on 8/16/14.
 */
abstract class PutGetDelWriteTest : DatabaseTestCase() {
    @Test
    @Throws(Exception::class)
    fun testPut() {
        val db = obtainLevelDB()
        db.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), true)
        db.put(byteArrayOf(1, 2, 3, 4), byteArrayOf(1, 2, 3, 4), false)
        db.put(byteArrayOf(1, 2, 3, 4, 5), null, false)

        db.close()
        var threw = false
        try {
            db.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), false)
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        Assert.assertTrue(threw)
    }

    @Test
    @Throws(Exception::class)
    fun testGet() {
        val db = obtainLevelDB()
        db.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), false)
        var result = db[byteArrayOf(1, 2, 3)]
        Assert.assertNotNull(result)
        Assert.assertEquals(0, lexicographicCompare(byteArrayOf(1, 2, 3), result).toLong())
        db.put(byteArrayOf(1, 2, 4), byteArrayOf(1, 2, 4), true)
        result = db[byteArrayOf(1, 2, 4)]
        Assert.assertNotNull(result)
        Assert.assertEquals(0, lexicographicCompare(byteArrayOf(1, 2, 4), result).toLong())
        db.put(byteArrayOf(1, 2, 4), null, false)
        result = db[byteArrayOf(1, 2, 4)]
        Assert.assertNull(result)
        db.close()
        var threw = false
        try {
            db[byteArrayOf(1, 2, 3)]
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        Assert.assertTrue(threw)
    }

    @Test
    @Throws(Exception::class)
    fun testDel() {
        val db = obtainLevelDB()
        db.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), false)
        Assert.assertNotNull(db[byteArrayOf(1, 2, 3)])
        db.del(byteArrayOf(1, 2, 3), false)
        Assert.assertNull(db[byteArrayOf(1, 2, 3)])
        db.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3), false)
        Assert.assertNotNull(db[byteArrayOf(1, 2, 3)])
        db.del(byteArrayOf(1, 2, 3), true)
        Assert.assertNull(db[byteArrayOf(1, 2, 3)])

        db.close()
        var threw = false
        try {
            db.del(byteArrayOf(1, 2, 3), false)
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        Assert.assertTrue(threw)
    }

    @Test
    @Throws(Exception::class)
    fun testWrite() {
        val db = obtainLevelDB()
        var swb = SimpleWriteBatch(db)
        swb.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3))
        swb.put(byteArrayOf(1, 2, 3, 4), byteArrayOf(1, 2, 3, 4))
        db.write(swb, false)
        Assert.assertNotNull(db[byteArrayOf(1, 2, 3)])
        Assert.assertNotNull(db[byteArrayOf(1, 2, 3, 4)])
        swb = SimpleWriteBatch(db)
        swb.put(byteArrayOf(1, 2, 3, 4), byteArrayOf(1, 2, 3))
        swb.del(byteArrayOf(1, 2, 3))
        db.write(swb, true)
        Assert.assertNotNull(db[byteArrayOf(1, 2, 3, 4)])
        Assert.assertEquals(
            0,
            lexicographicCompare(db[byteArrayOf(1, 2, 3, 4)], byteArrayOf(1, 2, 3)).toLong()
        )
        Assert.assertNull(db[byteArrayOf(1, 2, 3)])

        db.close()
        var threw = false
        try {
            db.write(SimpleWriteBatch(db), false)
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        Assert.assertTrue(threw)
    }
}
