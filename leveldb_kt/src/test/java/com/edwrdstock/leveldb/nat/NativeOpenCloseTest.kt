package com.edwrdstock.leveldb.nat

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.implementation.NativeLevelDB
import com.edwrdstock.leveldb.common.DatabaseTestCase
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertFalse
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
/**
 * Created by hermann on 8/16/14.
 */
class NativeOpenCloseTest : DatabaseTestCase() {
    @Test
    @Throws(Exception::class)
    fun testCreateAndOpenNonExistingDatabase() {
        assertFalse(dbFile.exists())

        LevelDB.open(dbFile.absolutePath) {
            createIfMissing = true
        }.use {
            assertFalse(it.isClosed)
            it.close()
            it.close()
            assertTrue(dbFile.exists())
            assertTrue(it.isClosed)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testCreateAndOpenNonExistingDatabaseAutoClose() {
        assertFalse(dbFile.exists())
        LevelDB.open(dbFile.absolutePath) {
            createIfMissing = true
        }.use {

            assertFalse((it as NativeLevelDB).isClosed)
        }
        assertTrue(dbFile.exists())
    }

    @Test
    @Throws(Exception::class)
    fun testOpenAnExistingDatabase() {
        assertFalse(dbFile.exists())
        var ndb = NativeLevelDB(dbFile.absolutePath, LevelDB.Config(createIfMissing = true))
        ndb.close()
        assertTrue(dbFile.exists())
        ndb = NativeLevelDB(dbFile.absolutePath, LevelDB.Config(createIfMissing = false))
        ndb.close()
        assertTrue(dbFile.exists())
    }

    @Test
    @Throws(Exception::class)
    fun testTwiceOpenADatabase() {
        assertFalse(dbFile.exists())
        var threw = false
        val ndbA = NativeLevelDB(dbFile.absolutePath, LevelDB.Config(createIfMissing = true))

        try {
            NativeLevelDB(dbFile.absolutePath, LevelDB.Config(createIfMissing = true))
        } catch (e: LevelDBException) {
            threw = true
        }
        assertTrue(threw)
        ndbA.close()
        ndbA.close()

        assertTrue(ndbA.isClosed)
        assertTrue(ndbA.isClosed)
        assertTrue(dbFile.exists())
    }

    @Throws(Exception::class)
    override fun obtainLevelDB(): LevelDB {
        return NativeLevelDB(dbFile.absolutePath, LevelDB.Config(createIfMissing = true))
    }
}
