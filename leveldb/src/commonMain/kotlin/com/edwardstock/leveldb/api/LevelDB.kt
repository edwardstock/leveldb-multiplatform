package com.edwardstock.leveldb.api

import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.internal.levelDbLoadNativeLibrary


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
 */

/**
 * Main LevelDB API for direct database access
 */
interface LevelDB : AutoCloseable {
    companion object {
        const val DEFAULT_DBNAME = "default.ldb"

        init {
            levelDbLoadNativeLibrary()
        }
    }

    val config: LevelDBInstanceConfig

    /**
     * Closes this LevelDB instance. Database is usually not usable after a call to this method
     */
    override fun close()

    /**
     * Atomically check if this database has been closed
     * @return whether it's been closed
     */
    val isClosed: Boolean

    // reads
    fun getBytes(key: ByteArray, snapshot: Snapshot? = null): ByteArray?
    fun iterator(fillCache: Boolean = false, snapshot: Snapshot? = null): LevelDBIterator
    fun obtainSnapshot(): Snapshot
    fun getPropertyBytes(key: ByteArray): ByteArray?

    // writes
    fun putBytes(key: ByteArray, value: ByteArray?, sync: Boolean = false)
    fun write(writeBatch: WriteBatch, sync: Boolean = false)
    fun del(key: ByteArray, sync: Boolean = false)
    fun withBatch(
        batch: WriteBatch? = null,
        sync: Boolean = true,
        block: WriteBatch.() -> Unit,
    )
}
