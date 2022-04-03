package com.edwardstock.leveldb.implementation

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.WriteBatch
import com.edwardstock.leveldb.exception.LevelDBException
import java.lang.ref.WeakReference
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
 */
/**
 * A simple implementation of [com.edwardstock.leveldb.WriteBatch].
 */
class SimpleWriteBatch(db: LevelDB) : WriteBatch {
    /**
     * A [java.lang.ref.WeakReference] is used to allow for the easy and timely garbage collection of the [ ] instance by the GC, in case a WriteBatch reference wanders off.
     */
    private val ref: WeakReference<LevelDB> = WeakReference(db)
    private val refValue: LevelDB
        get() {
            assert(ref.get() != null) { "Getting null pointer of LevelDB" }
            return ref.get()!!
        }
    private val operations: LinkedList<WriteBatch.Operation> = LinkedList()

    /**
     * A simple implementation of [com.edwardstock.leveldb.WriteBatch.Operation].
     */
    private class Operation private constructor(
        private val type: Int,
        private val key: ByteArray,
        private val value: ByteArray?
    ) : WriteBatch.Operation {

        override fun key(): ByteArray {
            return key
        }

        override fun value(): ByteArray? {
            return value
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
     * Put the key-value pair in the database.
     * @param key the key to write
     * @param value the value to write
     * @return this WriteBatch for chaining
     */
    fun put(key: String, value: String?): SimpleWriteBatch {
        return value?.let {
            put(key, it)
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
        return del(key.toByteArray())
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

    /**
     * {@inheritDoc}
     */
    override fun iterator(): Iterator<WriteBatch.Operation> {
        return operations.iterator()
    }

    /**
     * {@inheritDoc}
     */
    override val allOperations: Collection<WriteBatch.Operation>
        get() = ArrayList(operations)

    /**
     * {@inheritDoc}
     */
    @Throws(LevelDBException::class)
    override fun commit(sync: Boolean) {
        refValue.write(this, sync)
    }

    /**
     * Whether this [SimpleWriteBatch] object is bound to its creating [ ] instance.
     *
     * You cannot use [.write] or variants on a non-bound instance since the database object does not
     * exist.
     *
     * @return
     * @see .write
     * @see com.edwardstock.leveldb.LevelDB.write
     */
    val isBound: Boolean
        get() = ref.get() != null


}