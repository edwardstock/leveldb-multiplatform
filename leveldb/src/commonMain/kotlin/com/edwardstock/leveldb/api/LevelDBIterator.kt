package com.edwardstock.leveldb.api

import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBIteratorNotValidException
import kotlin.reflect.KClass

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
 * Cursor-style iterator over key/value pairs
 */
interface LevelDBIterator : AutoCloseable {
    /**
     * Checks if there is a key-value pair over the current position of the iterator.
     *
     * Note: reading this property after the iterator (or its backing DB) is closed throws
     * [LevelDBClosedException], same as the methods below. It carries no `@Throws` annotation
     * because Kotlin's `@Throws` is not applicable to a property getter (only the function-style
     * members can declare it); the contract is documented here instead.
     *
     * @throws LevelDBClosedException if the iterator (or its backing DB) has been closed
     */
    val isValid: Boolean

    /**
     * Moves to the first key-value pair in the database
     *
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    fun seekToFirst()

    /**
     * Moves to the last key-value pair in the database
     *
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    fun seekToLast()

    /**
     * Moves on top of, or just after key, in the database
     *
     * @param key the key to seek, if null throws an [java.lang.IllegalArgumentException]
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    fun seek(key: ByteArray)

    /**
     * Moves to the next entry in the database
     *
     * @throws LevelDBIteratorNotValidException if not [.isValid]
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBIteratorNotValidException::class, LevelDBClosedException::class)
    operator fun next()

    /**
     * Moves to the previous entry in the database
     *
     * @throws LevelDBIteratorNotValidException if not [.isValid]
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBIteratorNotValidException::class, LevelDBClosedException::class)
    fun previous()

    /**
     * Returns the key under the iterator
     *
     * @return the key
     * @throws LevelDBIteratorNotValidException if not [.isValid]
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBIteratorNotValidException::class, LevelDBClosedException::class)
    fun key(): ByteArray

    fun keyString(): String {
        return key().decodeToString()
    }

    /**
     * Returns the value under the iterator
     *
     * @return the value
     * @throws LevelDBIteratorNotValidException if not [.isValid]
     * @throws LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    fun value(): ByteArray

    fun valueString(): String {
        return value().decodeToString()
    }

    @Throws(LevelDBClosedException::class)
    fun <T : Any> valueT(clazz: KClass<T>): T

    /**
     * Checks whether this iterator has been closed
     */
    val isClosed: Boolean

    /**
     * Closes this iterator if it has not been. It is usually unusable after a call to this method
     */
    override fun close()
}
