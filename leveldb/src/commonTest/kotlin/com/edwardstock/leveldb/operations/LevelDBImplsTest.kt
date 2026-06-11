package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.get
import com.edwardstock.leveldb.api.open
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.impl.SimpleWriteBatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LevelDBImplsTest {
    @Test
    fun `reader and writer impls delegate to db`() {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString())

        db.putBytes(byteArrayOf(1), byteArrayOf(2), sync = false)
        assertEquals(2, db[byteArrayOf(1)]?.first()?.toInt())

        val snapshot = db.obtainSnapshot()
        db.putBytes(byteArrayOf(1), byteArrayOf(3), sync = false)
        val snapValue = db.getBytes(byteArrayOf(1), snapshot)
        snapshot.close()
        assertEquals(2, snapValue?.first()?.toInt())

        val batch = SimpleWriteBatch().apply {
            put(byteArrayOf(9), byteArrayOf(9))
            del(byteArrayOf(1))
        }
        db.write(batch, sync = true)
        assertNull(db[byteArrayOf(1)])
        assertEquals(9, db[byteArrayOf(9)]?.first()?.toInt())

        db.withBatch(sync = false) {
            put(byteArrayOf(7), byteArrayOf(7))
            del(byteArrayOf(9))
        }
        assertEquals(7, db[byteArrayOf(7)]?.first()?.toInt())
        assertNull(db[byteArrayOf(9)])

        db.close()
    }
}
