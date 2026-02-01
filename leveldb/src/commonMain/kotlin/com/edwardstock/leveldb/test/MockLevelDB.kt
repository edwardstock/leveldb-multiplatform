package com.edwardstock.leveldb.test

import com.edwardstock.leveldb.LevelDbByteArrayComparator
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.LevelDBIterator
import com.edwardstock.leveldb.api.Snapshot
import com.edwardstock.leveldb.api.WriteBatch
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBIteratorNotValidException
import com.edwardstock.leveldb.impl.SimpleWriteBatch
import kotlin.reflect.KClass

/**
 * Pure-Kotlin in-memory LevelDB implementation for tests.
 *
 * Semantics:
 * - Keys are ordered lexicographically (same ordering as native)
 * - [putBytes] with `value == null` behaves like delete
 * - [getPropertyBytes] always returns null
 * - Snapshots are point-in-time, immutable views
 */
class MockLevelDB(
    override val config: LevelDBInstanceConfig = LevelDBInstanceConfig(),
    initial: Map<ByteArray, ByteArray?> = emptyMap(),
) : LevelDB {

    override val token: Any = Any()

    private var closed = false

    private var store = SortedBytesStore().apply {
        for ((k, v) in initial) {
            if (v != null) put(k, v) else remove(k)
        }
    }

    override val isClosed: Boolean
        get() = closed

    override fun close() {
        closed = true
    }

    override fun getBytes(key: ByteArray, snapshot: Snapshot?): ByteArray? {
        ensureOpen()
        val s = snapshot as? MockSnapshot
        return (s?.store ?: store).get(key)
    }

    override fun iterator(fillCache: Boolean, snapshot: Snapshot?): LevelDBIterator {
        ensureOpen()
        val s = snapshot as? MockSnapshot
        return MockIterator((s?.store ?: store).snapshotCopy(), config)
    }

    override fun obtainSnapshot(): Snapshot {
        ensureOpen()
        return MockSnapshot(store.snapshotCopy())
    }

    override fun getPropertyBytes(key: ByteArray): ByteArray? {
        ensureOpen()
        return null
    }

    override fun putBytes(key: ByteArray, value: ByteArray?, sync: Boolean) {
        ensureOpen()
        if (value == null) {
            store.remove(key)
        } else {
            store.put(key, value)
        }
    }

    override fun write(writeBatch: WriteBatch, sync: Boolean) {
        ensureOpen()
        // Apply atomically: build a copy, apply, then swap.
        val copy = store.snapshotCopy()
        for (op in writeBatch) {
            if (op.isDel || op.value() == null) {
                copy.remove(op.key())
            } else {
                copy.put(op.key(), op.value()!!)
            }
        }
        store = copy
    }

    override fun del(key: ByteArray, sync: Boolean) {
        ensureOpen()
        store.remove(key)
    }

    override fun withBatch(batch: WriteBatch?, sync: Boolean, block: WriteBatch.() -> Unit) {
        ensureOpen()
        val b = batch ?: SimpleWriteBatch(config)
        b.block()
        write(b, sync)
    }

    private fun ensureOpen() {
        if (closed) throw LevelDBClosedException()
    }

    private class MockSnapshot(val store: SortedBytesStore) : Snapshot {
        private var released = false

        override val isReleased: Boolean
            get() = released

        override fun close() {
            released = true
        }
    }

    private class MockIterator(
        private val snapshot: SortedBytesStore,
        private val config: LevelDBInstanceConfig,
    ) : LevelDBIterator {

        private var closed = false
        private val keys: List<ByteArray> = snapshot.keysOrdered()
        private var index: Int = -1

        override val isClosed: Boolean
            get() = closed

        override val isValid: Boolean
            get() {
                ensureOpen()
                return index in keys.indices
            }

        override fun seekToFirst() {
            ensureOpen()
            index = if (keys.isEmpty()) -1 else 0
        }

        override fun seekToLast() {
            ensureOpen()
            index = if (keys.isEmpty()) -1 else keys.lastIndex
        }

        override fun seek(key: ByteArray) {
            ensureOpen()
            index = lowerBound(keys, key)
            if (index !in keys.indices) index = -1
        }

        override fun next() {
            ensureOpen()
            ensureValid()
            index++
            if (index !in keys.indices) index = -1
        }

        override fun previous() {
            ensureOpen()
            ensureValid()
            index--
            if (index !in keys.indices) index = -1
        }

        override fun key(): ByteArray {
            ensureOpen()
            ensureValid()
            return keys[index].copyOf()
        }

        override fun value(): ByteArray {
            ensureOpen()
            ensureValid()
            return snapshot.get(keys[index]) ?: throw LevelDBIteratorNotValidException()
        }

        override fun <T : Any> valueT(clazz: KClass<T>): T {
            ensureOpen()
            val bytes = value()
            return config.adapterRegistry.decode(bytes, clazz)
        }

        override fun close() {
            closed = true
        }

        private fun ensureOpen() {
            if (closed) throw LevelDBClosedException()
        }

        private fun ensureValid() {
            if (!isValid) throw LevelDBIteratorNotValidException()
        }

        private fun lowerBound(sortedKeys: List<ByteArray>, key: ByteArray): Int {
            var low = 0
            var high = sortedKeys.size
            while (low < high) {
                val mid = (low + high) ushr 1
                val cmp = LevelDbByteArrayComparator.compare(sortedKeys[mid], key)
                if (cmp < 0) low = mid + 1 else high = mid
            }
            return low
        }
    }
}
