package com.edwardstock.leveldb.implementation.mock

import com.edwardstock.leveldb.Bytes
import com.edwardstock.leveldb.Iterator
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBCorruptionException
import com.edwardstock.leveldb.exception.LevelDBIteratorNotValidException
import java.util.*

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
open class MockIterator(map: SortedMap<ByteArray, ByteArray>) : Iterator() {
    override var isClosed = false
        protected set

    protected val snapshot: SortedMap<ByteArray, ByteArray> =
        Collections.unmodifiableSortedMap(TreeMap(map))
    protected val keys: ArrayList<ByteArray> = ArrayList(map.keys)

    init {
        Collections.sort(keys, Bytes.COMPARATOR)
    }

    var position = 0

    @get:Throws(LevelDBClosedException::class)
    override val isValid: Boolean
        get() {
            checkIfClosed()
            return position > -1 && position < keys.size
        }

    @Throws(LevelDBClosedException::class)
    override fun seekToFirst() {
        checkIfClosed()
        position = 0
    }

    @Throws(LevelDBClosedException::class)
    override fun seekToLast() {
        checkIfClosed()
        position = keys.size - 1
    }

    @Throws(LevelDBClosedException::class)
    override fun seek(key: ByteArray?) {
        checkIfClosed()
        position = Collections.binarySearch(keys, key, Bytes.COMPARATOR)
        if (position < 0) {
            position = -position - 1
        }
    }

    @Throws(LevelDBClosedException::class)
    override fun next() {
        checkIfClosed()
        if (!isValid) {
            throw LevelDBIteratorNotValidException()
        }
        position++
    }

    @Throws(LevelDBClosedException::class)
    override fun previous() {
        checkIfClosed()
        if (!isValid) {
            throw LevelDBIteratorNotValidException()
        }
        position--
    }

    @Throws(LevelDBClosedException::class)
    override fun key(): ByteArray {
        checkIfClosed()
        if (!isValid) {
            throw LevelDBIteratorNotValidException()
        }
        return keys[position]
    }

    @Throws(LevelDBClosedException::class)
    override fun value(): ByteArray {
        checkIfClosed()
        if (!isValid) {
            throw LevelDBIteratorNotValidException()
        }
        return snapshot[keys[position]]
            ?: throw LevelDBCorruptionException("invalid snapshot or key ${keys[position]} does not exists")
    }

    override fun close() {
        isClosed = true
    }

    @Throws(LevelDBClosedException::class)
    protected fun checkIfClosed() {
        if (isClosed) {
            throw LevelDBClosedException("Iterator has been closed.")
        }
    }
}