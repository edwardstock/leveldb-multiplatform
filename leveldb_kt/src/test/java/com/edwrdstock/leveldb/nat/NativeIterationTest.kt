package com.edwrdstock.leveldb.nat

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.WriteBatch
import com.edwardstock.leveldb.implementation.SimpleWriteBatch
import com.edwardstock.leveldb.implementation.forEachAll
import com.edwardstock.leveldb.implementation.forEachKeys
import com.edwardstock.leveldb.implementation.forEachValues
import com.edwrdstock.leveldb.common.IterationTest
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
 * Created by hermann on 8/18/14.
 */
class NativeIterationTest : IterationTest() {
    @Throws(Exception::class)
    override fun obtainLevelDB(): LevelDB {
        return db
    }

    @Test
    fun testBenchmark() {
        val db = obtainLevelDB()

        val batch = SimpleWriteBatch(db)
        var startTime = System.currentTimeMillis()
        for(i in 0..10000) {
            batch.put("key$i", "value$i")
        }
        batch.commit(true)
        val endTimeWriteBatch = System.currentTimeMillis() - startTime
        println("write batch time: " + (endTimeWriteBatch / 1000.0))


        startTime = System.currentTimeMillis()
        db.forEachAll { key, value ->
            key
            value
        }
        val endTimeIterateAll = System.currentTimeMillis() - startTime
        println("iterate all time: " + (endTimeIterateAll / 1000.0))

        startTime = System.currentTimeMillis()
        db.forEachKeys { key ->
            key
        }
        val endTimeIterateKeys = System.currentTimeMillis() - startTime
        println("iterate keys time: " + (endTimeIterateKeys / 1000.0))

        startTime = System.currentTimeMillis()
        db.forEachValues { value ->
            value
        }
        val endTimeIterateValues = System.currentTimeMillis() - startTime
        println("iterate values time: " + (endTimeIterateValues / 1000.0))
    }
}
