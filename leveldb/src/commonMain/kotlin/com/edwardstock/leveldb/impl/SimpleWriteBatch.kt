package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.api.WriteBatch
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
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OFz SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 */

/**
 * A simple implementation of [com.edwardstock.leveldb.WriteBatch]
 */
class SimpleWriteBatch(
    override val config: LevelDBInstanceConfig = LevelDBInstanceConfig(),
) : WriteBatch {
    private val operations = mutableListOf<WriteBatch.Operation>()

    /**
     * {@inheritDoc}
     */
    override val allOperations: Collection<WriteBatch.Operation>
        get() = ArrayList(operations)

    /**
     * A simple implementation of [com.edwardstock.leveldb.WriteBatch.Operation]
     */
    private data class Operation private constructor(
        private val type: Int,
        private val key: ByteArray,
        private val value: ByteArray?,
    ) : WriteBatch.Operation {

        override fun key(): ByteArray = key
        override fun value(): ByteArray? = value

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Operation

            if (type != other.type) return false
            if (!key.contentEquals(other.key)) return false
            if (value != null) {
                if (other.value == null) return false
                if (!value.contentEquals(other.value)) return false
            } else if (other.value != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = type
            result = 31 * result + key.contentHashCode()
            result = 31 * result + (value?.contentHashCode() ?: 0)
            return result
        }

        override val isPut: Boolean
            get() = type == PUT
        override val isDel: Boolean
            get() = type == DELETE

        companion object {
            const val PUT = 0
            const val DELETE = 1
            fun put(key: ByteArray, value: ByteArray?): Operation {
                return Operation(PUT, key, value)
            }

            fun del(key: ByteArray): Operation {
                return Operation(DELETE, key, null)
            }
        }
    }

    /**
     * Put the key-value pair in the database
     * @param key the key to write
     * @param value the value to write
     * @return this WriteBatch for chaining
     */
    fun put(key: String, value: String?): SimpleWriteBatch {
        return value?.let {
            put(key.encodeToByteArray(), it.encodeToByteArray())
        } ?: del(key)
    }

    /**
     * {@inheritDoc}
     */
    override fun put(key: ByteArray, value: ByteArray?): SimpleWriteBatch {
        if (value == null) {
            return del(key)
        }
        operations.add(Operation.put(key, value))
        return this
    }

    fun del(key: String): SimpleWriteBatch {
        return del(key.encodeToByteArray())
    }

    /**
     * {@inheritDoc}
     */
    override fun del(key: ByteArray): SimpleWriteBatch {
        operations.add(Operation.del(key))
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun insert(operation: WriteBatch.Operation): SimpleWriteBatch {
        operations.add(operation)
        return this
    }

    override fun remove(operation: WriteBatch.Operation): WriteBatch {
        operations.remove(operation)
        return this
    }

    /**
     * {@inheritDoc}
     */
    override fun iterator(): Iterator<WriteBatch.Operation> = operations.iterator()
}
