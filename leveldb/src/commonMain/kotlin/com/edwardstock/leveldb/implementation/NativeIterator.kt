package com.edwardstock.leveldb.implementation

import com.edwardstock.leveldb.DbIteratorHandle
import com.edwardstock.leveldb.LevelDBIterator
import com.edwardstock.leveldb.LevelDBNativeProvider
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBIteratorNotValidException
import kotlinx.atomicfu.atomic

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
 */

/**
 * An iterator is used to iterator over the entries in the database according to the total sort order imposed by the
 * comparator
 *
 * Thread-safety: iterator instances are not safe to share across threads
 */
open class NativeIterator(handle: DbIteratorHandle) : LevelDBIterator() {
    // Don't touch this or all hell breaks loose
    private var _handle by atomic<DbIteratorHandle?>(handle)
    private val handle get() = _handle ?: throw LevelDBClosedException("Iterator is closed")

    /**
     * Whether this pointer is valid. An iterator is valid iff it is positioned over a key-value pair
     * @return whether the iterator is valid
     * @throws com.edwardstock.leveldb.exception.LevelDBClosedException
     */
    override val isValid: Boolean
        get() {
            checkIfClosed()
            return LevelDBNativeProvider.impl.iteratorValidate(handle)
        }

    /**
     * Seeks to the first key-value pair in the database
     * @throws com.edwardstock.leveldb.exception.LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    override fun seekToFirst() {
        checkIfClosed()
        LevelDBNativeProvider.impl.iteratorSeekToFirst(handle)
    }

    /**
     * Seeks to the last key-value pair in the database
     *
     *
     * NB: Reverse iteration is somewhat slower than forward iteration
     * @throws com.edwardstock.leveldb.exception.LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    override fun seekToLast() {
        checkIfClosed()
        LevelDBNativeProvider.impl.iteratorSeekToLast(handle)
    }

    /**
     * Seek to the given key, or right after it
     * @param key the key, never <tt>null</tt>
     * @throws com.edwardstock.leveldb.exception.LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    override fun seek(key: ByteArray) {
        checkIfClosed()
        LevelDBNativeProvider.impl.iteratorSeek(handle, key)
    }

    /**
     * Advance the iterator forward
     *
     *
     * Requires: [.isValid]
     * @throws com.edwardstock.leveldb.exception.LevelDBClosedException
     */
    @Throws(LevelDBIteratorNotValidException::class, LevelDBClosedException::class)
    override fun next() {
        checkIfClosed()
        if (!isValid) {
            throw LevelDBIteratorNotValidException()
        }
        LevelDBNativeProvider.impl.iteratorNext(handle)
    }

    /**
     * Advance the iterator backward
     *
     *
     * Requires: [.isValid]
     * @throws com.edwardstock.leveldb.exception.LevelDBClosedException
     */
    @Throws(LevelDBIteratorNotValidException::class, LevelDBClosedException::class)
    override fun previous() {
        checkIfClosed()
        if (!isValid) {
            throw LevelDBIteratorNotValidException()
        }
        LevelDBNativeProvider.impl.iteratorPrev(handle)
    }

    /**
     * Get the key under the iterator
     *
     *
     * Requires: [.isValid]
     * @return the key under the iterator, <tt>null</tt> if invalid
     * @throws com.edwardstock.leveldb.exception.LevelDBClosedException
     */
    @Throws(LevelDBIteratorNotValidException::class, LevelDBClosedException::class)
    override fun key(): ByteArray {
        checkIfClosed()
        if (!isValid) {
            throw LevelDBIteratorNotValidException()
        }
        return LevelDBNativeProvider.impl.iteratorKey(handle)
    }

    /**
     * Get the value under the iterator
     *
     *
     * Requires: [.isValid]
     * @return the value under the iterator, <tt>null</tt> if invalid
     * @throws com.edwardstock.leveldb.exception.LevelDBClosedException
     */
    override fun value(): ByteArray {
        checkIfClosed()
        if (!isValid) {
            throw LevelDBIteratorNotValidException()
        }
        return LevelDBNativeProvider.impl.iteratorValue(handle)
    }

    /**
     * Whether this iterator has been closed
     * @return
     */
    override val isClosed: Boolean
        get() = _handle == null

    /**
     * Closes this iterator. It will be almost unusable after
     *
     *
     * Always close the iterator before closing the database
     */
    override fun close() {
        if (!isClosed) {
            LevelDBNativeProvider.impl.iteratorClose(handle)
        }
        _handle = null
    }

    /**
     * Checks if this iterator has been closed
     * @throws com.edwardstock.leveldb.exception.LevelDBClosedException
     */
    @Throws(LevelDBClosedException::class)
    private fun checkIfClosed() {
        if (isClosed) {
            throw LevelDBClosedException("Iterator has been closed.")
        }
    }


}
