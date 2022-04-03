package com.edwardstock.leveldb.implementation.mock


import com.edwardstock.leveldb.*
import com.edwardstock.leveldb.Iterator
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.exception.LevelDBSnapshotOwnershipException
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
open class MockLevelDB : LevelDB(Config()) {
    @Volatile
    override var isClosed = false
        protected set

    @JvmField
    val map: SortedMap<ByteArray, ByteArray> = TreeMap(Bytes.COMPARATOR)

    override fun close() {
        var multipleClose = false
        synchronized(this) {
            if (isClosed) {
                multipleClose = true
            } else {
                isClosed = true
            }
        }
        if (multipleClose) {
//            Log.i(MockLevelDB::class.java.name, "Trying to close Mock LevelDB multiple times.")
            isClosed = true
        }
    }

    @Synchronized
    @Throws(LevelDBException::class)
    override fun put(key: ByteArray, value: ByteArray?, sync: Boolean) {
        if (value == null) {
            del(key, sync)
            return
        }
        checkIfClosed()
        map[key] = value
    }

    @Synchronized
    @Throws(LevelDBException::class)
    override fun write(writeBatch: WriteBatch, sync: Boolean) {
        checkIfClosed()
        for (operation in writeBatch.allOperations) {
            if (operation.isDel) {
                map.remove(operation.key())
            } else {
                map[operation.key()] = operation.value()
            }
        }
    }

    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBException::class)
    override fun get(key: ByteArray, snapshot: Snapshot?): ByteArray? {
        if (snapshot != null) {
            if (snapshot !is MockSnapshot) {
                throw LevelDBSnapshotOwnershipException()
            }
            if (!snapshot.checkOwner(this)) {
                throw LevelDBSnapshotOwnershipException()
            }
        }
        synchronized(this) {
            checkIfClosed()
            return if (snapshot != null) {
                (snapshot as MockSnapshot).snapshot?.get(key)
            } else map[key]
        }
    }

    @Synchronized
    @Throws(LevelDBException::class)
    override fun del(key: ByteArray, sync: Boolean) {
        checkIfClosed()
        map.remove(key)
    }

    @Throws(LevelDBClosedException::class)
    override fun getPropertyBytes(key: ByteArray): ByteArray? {
        throw UnsupportedOperationException("Mock LevelDB does not support properties.")
    }

    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBClosedException::class)
    override fun iterator(fillCache: Boolean, snapshot: Snapshot?): Iterator {
        if (snapshot != null) {
            if (snapshot !is MockSnapshot) {
                throw LevelDBSnapshotOwnershipException()
            }
            if (!snapshot.checkOwner(this)) {
                throw LevelDBSnapshotOwnershipException()
            }
            return MockIterator(snapshot.snapshot!!)
        }
        synchronized(this) { return MockIterator(map) }
    }

    @Synchronized
    @Throws(LevelDBClosedException::class)
    override fun iterator(fillCache: Boolean): Iterator {
        return MockIterator(map)
    }

    // No-op.
    override var path: String = ":MOCK:"

    @Throws(LevelDBClosedException::class)
    override fun obtainSnapshot(): Snapshot? {
        return MockSnapshot(this)
    }

    @Throws(LevelDBSnapshotOwnershipException::class, LevelDBClosedException::class)
    override fun releaseSnapshot(snapshot: Snapshot?) {
        requireNotNull(snapshot) { "Snapshot must not be null." }
        if (snapshot !is MockSnapshot) {
            throw LevelDBSnapshotOwnershipException()
        }
        if (!snapshot.checkOwner(this)) {
            throw LevelDBSnapshotOwnershipException()
        }
        synchronized(this) {
            checkIfClosed()
            snapshot.release()
        }
    }

    @Throws(LevelDBClosedException::class)
    protected fun checkIfClosed() {
        if (isClosed) {
            throw LevelDBClosedException("Mock LevelDB has been closed.")
        }
    }
}