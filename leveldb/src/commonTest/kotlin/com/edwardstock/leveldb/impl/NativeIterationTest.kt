package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.forEachAll
import com.edwardstock.leveldb.api.forEachAllKeyString
import com.edwardstock.leveldb.api.forEachAllValueString
import com.edwardstock.leveldb.common.IterationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
 */ class NativeIterationTest : IterationTest() {
    @Throws(Exception::class)
    override fun obtainLevelDB(): LevelDB {
        return db
    }

    private fun LevelDB.limitIterator(limit: Int = -1, offset: Int = -1): List<String> {
        val result = ArrayList<String>(if (limit > 0) limit else 20)

        var i = 0
        var c = 0
        iterator().use {
            it.seekToFirst()
            while (it.isValid) {
                if (offset > 0 && c < offset) {
                    it.next()
                    c++
                    continue
                }
                result.add(it.valueString())
//                block(it.keyString(), it.valueString())
                it.next()
                i++
                c++
                if (i >= limit) {
                    break
                }
            }
        }
        return result
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testBenchmark() {
        val db = obtainLevelDB()
        val itemsCount = 100000

        val batch = SimpleWriteBatch()
        var startTime = Clock.System.now()
        for (i in 0..itemsCount) {
            batch.put("key$i", "value$i")
        }
        db.write(batch, true)
        val endTimeWriteBatch = Clock.System.now() - startTime
        println("write batch ($itemsCount elements) time: " + (endTimeWriteBatch / 1000.0))


        startTime = Clock.System.now()
        db.forEachAll {
            it.keyString()
            it.valueString()
        }
        val endTimeIterateAll = Clock.System.now() - startTime
        println("iterate all ($itemsCount elements) time: " + (endTimeIterateAll / 1000.0))

        startTime = Clock.System.now()
        db.forEachAllKeyString { key ->

        }
        val endTimeIterateKeys = Clock.System.now() - startTime
        println("iterate keys ($itemsCount elements) time: " + (endTimeIterateKeys / 1000.0))

        startTime = Clock.System.now()
        db.forEachAllValueString {}
        val endTimeIterateValues = Clock.System.now() - startTime
        println("iterate values ($itemsCount elements) time: " + (endTimeIterateValues / 1000.0))

        val limit = 10000
        val offset = 10000

        startTime = Clock.System.now()
        val allValues = ArrayList<String>(itemsCount)
        db.forEachAllValueString {
            allValues.add(it)
        }
        val endTimeGetAll = Clock.System.now() - startTime
        val limitExpected1 = allValues.subList(0, limit)
        val limitExpected2 = allValues.subList(limit, limit * 2)
        println("get all values ($itemsCount elements) and slicing 2 arrays (by $limit elements)  time: " + (endTimeGetAll / 1000.0))

        startTime = Clock.System.now()
        val limitRes1 = db.limitIterator(limit, 0)
        val limitRes2 = db.limitIterator(limit, offset)
        val endTimeLimitIterator = Clock.System.now() - startTime
        println("2 limit iterators (by $limit elements) time: " + (endTimeLimitIterator / 1000.0))

        assertEquals(limit, limitRes1.size)
        assertEquals(limit, limitRes2.size)
        assertEquals(limitExpected1, limitRes1)
        assertEquals(limitExpected2, limitRes2)


    }
}
