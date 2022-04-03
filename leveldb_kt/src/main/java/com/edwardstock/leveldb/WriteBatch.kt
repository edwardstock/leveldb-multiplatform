package com.edwardstock.leveldb

import com.edwardstock.leveldb.exception.LevelDBException

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
 * Holds a batch write operation. (Something like a transaction.)
 */
interface WriteBatch : Iterable<WriteBatch.Operation> {
    /**
     * Interace for a WriteBatch operation. LevelDB supports puts and deletions.
     */
    interface Operation {
        /**
         * The key to put or delete.
         *
         * @return the key, never null
         */
        fun key(): ByteArray

        /**
         * The value to associate with [.key].
         *
         * @return could be <tt>null</tt>, especially if [.isDel] <tt>== true</tt>
         */
        fun value(): ByteArray?

        /**
         * Whether this operation is a put.
         *
         * @return
         */
        val isPut: Boolean

        /**
         * Whether this operation is a delete.
         *
         * @return
         */
        val isDel: Boolean
    }

    /**
     * Put the key-value pair in the database.
     *
     * @param key   the key to write
     * @param value the value to write
     * @return this WriteBatch for chaining
     */
    fun put(key: ByteArray, value: ByteArray?): WriteBatch

    /**
     * Delete the key from the database.
     *
     * @param key the key to delete
     * @return this WriteBatch for chaining
     */
    fun del(key: ByteArray): WriteBatch

    /**
     * Insert a [com.edwardstock.leveldb.WriteBatch.Operation] in this WriteBatch.
     *
     * @param operation the operation to insert
     * @return this WriteBatch for chaining
     */
    fun insert(operation: Operation): WriteBatch

    /**
     * Get all operations in this WriteBatch.
     *
     * @return never null
     */
    val allOperations: Collection<Operation>

    /**
     * Commit this WriteBatch to the database.
     *
     * @param levelDB the [com.edwardstock.leveldb.implementation.NativeLevelDB] database to write to
     * @param sync    whether this is a synchronous (true) or asynchronous (false) write
     * @throws LevelDBException
     */
    @Throws(LevelDBException::class)
    fun commit(sync: Boolean = false)
}