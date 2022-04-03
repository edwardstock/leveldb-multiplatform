package com.edwrdstock.leveldb.mock

import com.edwardstock.leveldb.Bytes.lexicographicCompare
import com.edwardstock.leveldb.implementation.mock.MockLevelDB
import junit.framework.TestCase
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
 */
/**
 * Created by hermann on 8/17/14.
 */
class MockInteratorOrderingTest : TestCase() {
    @Test
    @Throws(Exception::class)
    fun testOrdering() {
        val mock = MockLevelDB()
        mock.put(byteArrayOf(0, 0, 1), byteArrayOf(1), false)
        mock.put(byteArrayOf(0, 0, 2), byteArrayOf(2), false)
        mock.put(byteArrayOf(0, 0, 3), byteArrayOf(3), false)
        var iterator = mock.iterator(false)
        iterator.seekToFirst()
        var i = 1
        while (iterator.isValid) {
            val key = iterator.key()
            val value = iterator.value()
            assertNotNull(key)
            assertNotNull(value)
            assertEquals(i.toByte(), key[key.size - 1] and 0xFF.toByte())
            assertEquals(i.toByte(), value[0] and 0xFF.toByte())
            iterator.next()
            i++
        }
        assertEquals(4, i)
        iterator.close()
        mock.put(byteArrayOf(0, 0, 10), byteArrayOf(10), false)
        iterator = mock.iterator()
        iterator.seek(byteArrayOf(0, 0, 4))
        assertTrue(iterator.isValid)
        var key = iterator.key()
        assertTrue(lexicographicCompare(byteArrayOf(0, 0, 10), key) == 0)
        iterator.previous()
        assertTrue(iterator.isValid)
        key = iterator.key()
        assertTrue(lexicographicCompare(byteArrayOf(0, 0, 3), key) == 0)
        iterator.close()
        mock.close()
    }

    @Test
    @Throws(Exception::class)
    fun testClosed() {
        val mock = MockLevelDB()
        val iterator = mock.iterator(true)
        mock.close()
        iterator.close()
        assertTrue(mock.isClosed)
        assertTrue(iterator.isClosed)
    }
}
