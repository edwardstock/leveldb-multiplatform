package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.implementation.SimpleWriteBatch
import com.edwardstock.leveldb.utils.open
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LevelDBImplsTest {
    @Test
    fun `reader and writer impls delegate to db`() {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString()) { createIfMissing = true }

        val reader = LevelDBOpsImpl(db)
        val writer = LevelDBOpsImpl(db)

        writer.put(byteArrayOf(1), byteArrayOf(2), sync = false)
        assertEquals(2, reader.get(byteArrayOf(1))?.first()?.toInt())

        val snapshot = reader.obtainSnapshot()
        writer.put(byteArrayOf(1), byteArrayOf(3), sync = false)
        val snapValue = reader.get(byteArrayOf(1), snapshot)
        snapshot.close()
        assertEquals(2, snapValue?.first()?.toInt())

        val batch = SimpleWriteBatch().apply {
            put(byteArrayOf(9), byteArrayOf(9))
            del(byteArrayOf(1))
        }
        writer.write(batch, sync = true)
        assertNull(reader.get(byteArrayOf(1)))
        assertEquals(9, reader.get(byteArrayOf(9))?.first()?.toInt())

        writer.withBatch(sync = false) {
            put(byteArrayOf(7), byteArrayOf(7))
            del(byteArrayOf(9))
        }
        assertEquals(7, reader.get(byteArrayOf(7))?.first()?.toInt())
        assertNull(reader.get(byteArrayOf(9)))

        db.close()
    }
}
