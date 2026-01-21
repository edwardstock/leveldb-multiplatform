package com.edwardstock.leveldb

import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.utils.open
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LevelDBApiTest {
    @Test
    fun `string overloads and snapshots work`() {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString()) { createIfMissing = true }

        db.put("k", "v1")
        val snapshot = db.obtainSnapshot()
        db.put("k", "v2")

        val snapValue = db.getString("k", snapshot)
        val current = db.getString("k")
        snapshot.close()

        assertEquals("v1", snapValue)
        assertEquals("v2", current)

        db.del("k")
        assertNull(db.getString("k"))

        db.close()
    }

    @Test
    fun `open path overload delegates to string open`() {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path, LevelDBConfig(createIfMissing = true))
        db.put("k", "v")
        assertEquals("v", db.getString("k"))
        db.close()
    }

    @Test
    fun `byte array overloads and iterator work`() {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString()) { createIfMissing = true }

        val key = byteArrayOf(1, 2, 3)
        val value = byteArrayOf(4, 5, 6)
        db.put(key, value)

        val read = db[key]
        assertEquals(0, com.edwardstock.leveldb.utils.BytesHelper.lexicographicCompare(read, value))

        val iterator = db.iterator(fillCache = false)
        iterator.seekToFirst()
        val iterValue = iterator.valueString()
        iterator.close()
        assertEquals(value.decodeToString(), iterValue)

        db.del(key)
        assertNull(db[key])
        db.close()
    }
}
