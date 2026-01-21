package com.edwardstock.leveldb.implementation

import com.edwardstock.leveldb.LevelDBConfig
import com.edwardstock.leveldb.Snapshot
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBSnapshotOwnershipException
import com.edwardstock.leveldb.util.absolutePath
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeLevelDBBehaviorTest {
    private fun newDb() = NativeLevelDB(
        DatabaseTestCase.createRandomDbPath().absolutePath,
        LevelDBConfig(createIfMissing = true)
    )

    private class TestSnapshot : Snapshot() {
        override val isReleased: Boolean = false
        override fun close() = Unit
    }

    @Test
    fun `snapshot ownership is enforced`() {
        val dbA = newDb()
        val dbB = newDb()
        val snapshot = dbA.obtainSnapshot()

        try {
            assertFailsWith<LevelDBSnapshotOwnershipException> {
                dbB.get(byteArrayOf(1), snapshot)
            }
            assertFailsWith<LevelDBSnapshotOwnershipException> {
                dbB.iterator(snapshot = snapshot)
            }
            assertFailsWith<LevelDBSnapshotOwnershipException> {
                dbA.get(byteArrayOf(1), TestSnapshot())
            }
        } finally {
            snapshot.close()
            dbA.close()
            dbB.close()
        }
    }

    @Test
    fun `snapshot is released on db close`() {
        val db = newDb()
        val snapshot = db.obtainSnapshot()
        db.close()

        assertTrue(snapshot.isReleased)
    }

    @Test
    fun `put null removes key`() {
        val db = newDb()
        val key = "key".encodeToByteArray()

        db.put(key, "value".encodeToByteArray(), sync = false)
        db.put(key, null, sync = false)

        assertNull(db.get(key))
        db.close()
    }

    @Test
    fun `closed db rejects operations`() {
        val db = newDb()
        db.close()

        assertFailsWith<LevelDBClosedException> {
            db.get(byteArrayOf(1))
        }
        assertFailsWith<LevelDBClosedException> {
            db.put(byteArrayOf(1), byteArrayOf(2), sync = false)
        }
        assertFailsWith<LevelDBClosedException> {
            db.getPropertyBytes("leveldb.stats".encodeToByteArray())
        }
        assertFailsWith<LevelDBClosedException> {
            db.iterator()
        }
    }
}
