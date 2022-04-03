package com.edwrdstock.leveldb.common

import com.edwardstock.leveldb.Bytes
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBIteratorNotValidException
import com.edwardstock.leveldb.implementation.SimpleWriteBatch
import org.junit.Assert
import org.junit.Test
import kotlin.experimental.and

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
 */ abstract class IterationTest : DatabaseTestCase() {
    @Test
    @Throws(Exception::class)
    fun testIteration() {
        val db = obtainLevelDB()
        val wb = SimpleWriteBatch(db)
        wb.put(byteArrayOf(0, 0, 1), byteArrayOf(1))
        wb.put(byteArrayOf(0, 0, 2), byteArrayOf(2))
        wb.put(byteArrayOf(0, 0, 3), byteArrayOf(3))
        wb.commit()
        var iterator = db.iterator()
        db.put(byteArrayOf(0, 0, 0), byteArrayOf(0))
        iterator.seekToFirst()
        var i: Byte = 1
        while (iterator.isValid) {
            val key = iterator.key()
            val `val` = iterator.value()
            Assert.assertNotNull(key)
            Assert.assertEquals(0, Bytes.lexicographicCompare(key, byteArrayOf(0, 0, i)).toLong())
            Assert.assertEquals(0, Bytes.lexicographicCompare(`val`, byteArrayOf(i)).toLong())
            iterator.next()
            i++
        }
        Assert.assertEquals(4, (i and 0xFF.toByte()).toLong())
        iterator.close()
        iterator.close()
        iterator = db.iterator()
        iterator.seekToLast()
        Assert.assertTrue(iterator.isValid)
        i = 3
        while (iterator.isValid) {
            val key = iterator.key()
            val `val` = iterator.value()
            Assert.assertNotNull(key)
            Assert.assertEquals(0, Bytes.lexicographicCompare(key, byteArrayOf(0, 0, i)).toLong())
            Assert.assertEquals(0, Bytes.lexicographicCompare(`val`, byteArrayOf(i)).toLong())
            iterator.previous()
            i--
        }
        Assert.assertEquals(255L, (i.toLong() and 0xFFL))
        var threw = false
        try {
            iterator.next()
        } catch (e: LevelDBIteratorNotValidException) {
            threw = true
        }
        Assert.assertTrue(threw)
        threw = false
        try {
            iterator.previous()
        } catch (e: LevelDBIteratorNotValidException) {
            threw = true
        }
        Assert.assertTrue(threw)
        iterator.close()
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
        Assert.assertTrue(threw)
        threw = false
        try {
            iterator.next()
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        Assert.assertTrue(threw)
        threw = false
        try {
            iterator.previous()
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        Assert.assertTrue(threw)
        threw = false
        try {
            iterator.key()
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        Assert.assertTrue(threw)
        threw = false
        try {
            iterator.value()
        } catch (e: LevelDBClosedException) {
            threw = true
        }
        Assert.assertTrue(threw)
        db.close()
    }
}
