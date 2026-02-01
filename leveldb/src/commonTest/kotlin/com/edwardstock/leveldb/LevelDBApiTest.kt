package com.edwardstock.leveldb

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.del
import com.edwardstock.leveldb.api.get
import com.edwardstock.leveldb.api.getString
import com.edwardstock.leveldb.api.open
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.internal.BytesHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LevelDBApiTest {
    @Test
    fun `string overloads and snapshots work`() {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString())

        db.putString("k", "v1")
        val snapshot = db.obtainSnapshot()
        db.putString("k", "v2")

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
        val db = LevelDB.open(path)
        db.putString("k", "v")
        assertEquals("v", db.getString("k"))
        db.close()
    }

    @Test
    fun `byte array overloads and iterator work`() {
        val path = DatabaseTestCase.createRandomDbPath()
        val db = LevelDB.open(path.toString())

        val key = byteArrayOf(1, 2, 3)
        val value = byteArrayOf(4, 5, 6)
        db.putBytes(key, value)

        val read = db[key]
        assertEquals(0, BytesHelper.lexicographicCompare(read, value))

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
