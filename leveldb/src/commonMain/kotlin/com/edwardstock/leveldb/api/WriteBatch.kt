package com.edwardstock.leveldb.api

import com.edwardstock.leveldb.config.LevelDBInstanceConfig

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
 * Holds a batch write operation. (Something like a transaction.)
 */
interface WriteBatch : Iterable<WriteBatch.Operation> {
    val config: LevelDBInstanceConfig

    /**
     * A single operation inside a [WriteBatch]: either a put or a delete.
     *
     * Invariant: the value's nullability is the single source of truth.
     * `value() == null` means delete; a non-null value means put. [isPut]/[isDel] are derived
     * from it and must NOT be overridden to contradict the value.
     */
    interface Operation {
        /**
         * The key to put or delete
         *
         * @return the key, never null
         */
        fun key(): ByteArray

        /**
         * The value associated with [key].
         *
         * @return the value for a put, or `null` for a delete
         */
        fun value(): ByteArray?

        /** Whether this operation is a put. Derived from [value]: a put has a non-null value. */
        val isPut: Boolean get() = value() != null

        /** Whether this operation is a delete. Derived from [value]: a delete has a null value. */
        val isDel: Boolean get() = value() == null
    }

    /**
     * Queue a put for the key-value pair.
     *
     * Passing a `null` [value] is equivalent to [del] (the key will be deleted). A put always stores
     * a non-null value; use `ByteArray(0)` for an empty value.
     *
     * @param key   the key to write
     * @param value the value to write, or `null` to delete the key
     * @return this WriteBatch for chaining
     */
    fun put(key: ByteArray, value: ByteArray?): WriteBatch

    /**
     * Delete the key from the database
     *
     * @param key the key to delete
     * @return this WriteBatch for chaining
     */
    fun del(key: ByteArray): WriteBatch

    /**
     * Insert a [com.edwardstock.leveldb.WriteBatch.Operation] in this WriteBatch
     *
     * @param operation the operation to insert
     * @return this WriteBatch for chaining
     */
    fun insert(operation: Operation): WriteBatch

    fun remove(operation: Operation): WriteBatch

    /**
     * Get all operations in this WriteBatch
     *
     * @return never null
     */
    val allOperations: Collection<Operation>
}
