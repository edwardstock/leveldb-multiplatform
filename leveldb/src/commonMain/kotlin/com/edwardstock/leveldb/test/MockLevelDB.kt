package com.edwardstock.leveldb.test

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.LevelDBIterator
import com.edwardstock.leveldb.api.Snapshot
import com.edwardstock.leveldb.api.WriteBatch
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBIteratorNotValidException
import com.edwardstock.leveldb.impl.SimpleWriteBatch
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import kotlin.reflect.KClass

// TODO - move to a separate module

/**
 * Pure-Kotlin in-memory LevelDB implementation.
 *
 * Intended for tests and for swapping into [com.edwardstock.leveldb.LevelDBInstance] via `dbFactory`.
 *
 * Semantics:
 * - Keys are ordered lexicographically (same ordering as native)
 * - [putBytes] with `value == null` behaves like delete
 * - [getPropertyBytes] always returns null
 * - Snapshots are point-in-time, immutable views
 */
class MockLevelDB : LevelDB {

    /**
     * Primary ctor used by [com.edwardstock.leveldb.api.LevelDBInstanceFactory] (where `path` is available).
     *
     * Store is persisted per path, so multiple opens of the same path share the same data.
     */
    constructor(
        path: String,
        config: LevelDBInstanceConfig = LevelDBInstanceConfig(),
        initial: Map<ByteArray, ByteArray?> = emptyMap(),
    ) {
        this.path = path
        this.config = config
        this.store = acquireStore(path, initial)
    }

    /**
     * Convenience ctor. Uses a unique in-memory path (so different instances won't share state).
     */
    constructor(
        config: LevelDBInstanceConfig = LevelDBInstanceConfig(),
        initial: Map<ByteArray, ByteArray?> = emptyMap(),
    ) : this(path = nextInMemoryPath(), config = config, initial = initial)

    private val path: String

    override val config: LevelDBInstanceConfig

    private var closed = false

    private var store: SortedBytesStore

    override val isClosed: Boolean
        get() = closed

    override fun close() {
        closed = true
    }

    override fun getBytes(key: ByteArray, snapshot: Snapshot?): ByteArray? {
        ensureOpen()
        return readStoreFor(snapshot).get(key)
    }

    override fun iterator(fillCache: Boolean, snapshot: Snapshot?): LevelDBIterator {
        ensureOpen()
        // Iterator must be stable even if DB is modified later -> take a snapshot copy.
        return MockIterator(readStoreFor(snapshot).snapshotCopy(), config)
    }

    /**
     * Resolves which store a read should observe.
     *
     * A live (unreleased) [MockSnapshot] serves its captured point-in-time copy. A RELEASED
     * snapshot falls back to the live store — matching native LevelDB, where a released snapshot's
     * handle is null and reads observe the live database rather than the stale point-in-time view.
     */
    private fun readStoreFor(snapshot: Snapshot?): SortedBytesStore {
        val s = snapshot as? MockSnapshot
        return if (s != null && !s.isReleased) s.store else store
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
        // value nullability is the single source of truth: null == delete (see WriteBatch.Operation)
        val copy = store.snapshotCopy()
        for (op in writeBatch) {
            val v = op.value()
            if (v == null) {
                copy.remove(op.key())
            } else {
                copy.put(op.key(), v)
            }
        }
        store = copy
        // Persist the swapped store into registry for this path.
        updateStore(path, store)
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
            val ceil = snapshot.ceilingKey(key)
            if (ceil == null) {
                index = -1
                return
            }
            // keys are copies; match by content
            index = keys.indexOfFirst { it.contentEquals(ceil) }
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
    }

    private companion object {
        private val registryLock = SynchronizedObject()
        private val storesByPath = mutableMapOf<String, SortedBytesStore>()
        private var inMemoryCounter = 0

        private fun nextInMemoryPath(): String = synchronized(registryLock) {
            inMemoryCounter += 1
            "(in-memory-$inMemoryCounter)"
        }

        private fun acquireStore(path: String, initial: Map<ByteArray, ByteArray?>): SortedBytesStore {
            return synchronized(registryLock) {
                val existing = storesByPath[path]
                if (existing != null) return@synchronized existing

                val created = SortedBytesStore().apply {
                    for ((k, v) in initial) {
                        if (v != null) put(k, v) else remove(k)
                    }
                }
                storesByPath[path] = created
                created
            }
        }

        private fun updateStore(path: String, store: SortedBytesStore) {
            synchronized(registryLock) {
                storesByPath[path] = store
            }
        }
    }
}

fun LevelDBInstance.Companion.mock(
    filePath: String = "mock",
    initialData: Map<ByteArray, ByteArray?> = emptyMap(),
    fileSystem: FileSystem = FakeFileSystem(),
    builder: LevelDBInstance.Builder.() -> Unit = {}
): LevelDBInstance = LevelDBInstance.builder(filePath) {
    instance {
        dbFactory { path, config -> MockLevelDB(path, config, initialData) }
        fileSystem(fileSystem)
    }
    builder()
}
