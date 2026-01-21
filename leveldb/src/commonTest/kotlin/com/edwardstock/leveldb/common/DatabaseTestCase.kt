package com.edwardstock.leveldb.common

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.util.exists
import com.edwardstock.leveldb.utils.open
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random
import kotlin.random.nextUInt

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
 */ abstract class DatabaseTestCase {
    companion object {
        fun createRandomDbPath(): Path {
            return FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "${Random.nextUInt()}.leveldb"
        }
    }

    protected val dbFile: Path by lazy {
        createRandomDbPath()
    }

    protected val db: LevelDB by lazy {
        LevelDB.open(dbFile.toString()) {
            createIfMissing = true
        }
    }


    @Throws(Exception::class)
    fun tearDown() {
        if (dbFile.exists()) {
            FileSystem.SYSTEM.delete(dbFile)
        }
    }

    @Throws(Exception::class)
    protected abstract fun obtainLevelDB(): LevelDB
}
