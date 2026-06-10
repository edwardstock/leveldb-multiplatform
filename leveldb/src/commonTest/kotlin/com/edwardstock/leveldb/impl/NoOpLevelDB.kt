package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.LevelDBIterator
import com.edwardstock.leveldb.api.Snapshot
import com.edwardstock.leveldb.api.WriteBatch
import com.edwardstock.leveldb.config.LevelDBInstanceConfig

internal class NoOpLevelDB(
    override val config: LevelDBInstanceConfig,
    private val onClose: () -> Unit
) : LevelDB {
    override val isClosed: Boolean get() = closed
    private var closed = false

    override fun close() {
        if (!closed) {
            closed = true
            onClose()
        }
    }

    override fun getBytes(key: ByteArray, snapshot: Snapshot?): ByteArray? = null

    override fun iterator(fillCache: Boolean, snapshot: Snapshot?): LevelDBIterator = NoOpLevelDBIterator()

    override fun obtainSnapshot(): Snapshot = object : Snapshot {
        override val isReleased: Boolean = false
        override fun close() = Unit
    }

    override fun getPropertyBytes(key: ByteArray): ByteArray? = null
    override fun putBytes(key: ByteArray, value: ByteArray?, sync: Boolean) = Unit
    override fun write(writeBatch: WriteBatch, sync: Boolean) = Unit
    override fun del(key: ByteArray, sync: Boolean) = Unit
    override fun withBatch(batch: WriteBatch?, sync: Boolean, block: WriteBatch.() -> Unit) = Unit
}
