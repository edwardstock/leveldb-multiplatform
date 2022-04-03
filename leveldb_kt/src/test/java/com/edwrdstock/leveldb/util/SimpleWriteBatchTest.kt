package com.edwrdstock.leveldb.util

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.implementation.SimpleWriteBatch
import com.edwrdstock.leveldb.common.DatabaseTestCase
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
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
class SimpleWriteBatchTest : DatabaseTestCase() {
    @Test
    @Throws(Exception::class)
    fun testOperations() {
        val writeBatch = SimpleWriteBatch(db)
        writeBatch.put(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3))
        assertEquals(1, writeBatch.allOperations.size)
        writeBatch.del(byteArrayOf(1, 2, 3))
        assertEquals(2, writeBatch.allOperations.size)
        var del = 0
        var put = 0
        for (operation in writeBatch) {
            if (operation.isPut) {
                put++
            } else if (operation.isDel) {
                del++
            }
            assertNotNull(operation.key())
            if (!operation.isDel) {
                assertNotNull(operation.value())
            }
        }
        assertEquals(1, del)
        assertEquals(1, put)
        assertEquals(2, writeBatch.allOperations.size)
    }

    override fun obtainLevelDB(): LevelDB {
        return db
    }
}