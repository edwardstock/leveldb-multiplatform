package com.edwardstock.leveldb

import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBIteratorNotValidException
import java.io.Closeable

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
abstract class Iterator : Closeable {
    /**
     * Checks if there is a key-value pair over the current position of the iterator.
     *
     * @throws LevelDBClosedException
     */
    @get:Throws(LevelDBClosedException::class)
    abstract val isValid: Boolean

    /**
     * Moves to the first key-value pair in the database.
     *
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    abstract fun seekToFirst()

    /**
     * Moves to the last key-value pair in the database.
     *
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    abstract fun seekToLast()

    /**
     * Moves on top of, or just after key, in the database.
     *
     * @param key the key to seek, if null throws an [java.lang.IllegalArgumentException]
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    abstract fun seek(key: ByteArray?)

    /**
     * Moves to the next entry in the database.
     *
     * @throws LevelDBIteratorNotValidException if not [.isValid]
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBIteratorNotValidException::class, LevelDBClosedException::class)
    abstract operator fun next()

    /**
     * Moves to the previous entry in the database.
     *
     * @throws LevelDBIteratorNotValidException if not [.isValid]
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBIteratorNotValidException::class, LevelDBClosedException::class)
    abstract fun previous()

    /**
     * Returns the key under the iterator.
     *
     * @return the key
     * @throws LevelDBIteratorNotValidException if not [.isValid]
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBIteratorNotValidException::class, LevelDBClosedException::class)
    abstract fun key(): ByteArray

    fun keyString(): String {
        return String(key())
    }

    /**
     * Returns the value under the iterator.
     *
     * @return the value
     * @throws LevelDBIteratorNotValidException if not [.isValid]
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    abstract fun value(): ByteArray

    fun valueString(): String {
        return String(value())
    }

    /**
     * Checks whether this iterator has been closed.
     */
    abstract val isClosed: Boolean

    /**
     * Closes this iterator if it has not been. It is usually unusable after a call to this method.
     */
    abstract override fun close()
}