package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.AdapterRegistry
import com.edwardstock.leveldb.api.ValueAdapter
import com.edwardstock.leveldb.api.del
import com.edwardstock.leveldb.api.get
import com.edwardstock.leveldb.api.getBytes
import com.edwardstock.leveldb.api.getPropertyString
import com.edwardstock.leveldb.api.getString
import com.edwardstock.leveldb.api.getValue
import com.edwardstock.leveldb.api.putBytes
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.api.putValue
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.impl.NativeLevelDB
import com.edwardstock.leveldb.util.absolutePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelDBDefaultImplsTest {
    private class IntAdapter : ValueAdapter<Int> {
        override fun decode(value: ByteArray): Int {
            return value.let { 5 }
        }

        override fun encode(value: Int): ByteArray {
            return value.toString().encodeToByteArray()
        }
    }

    private fun newDb(): NativeLevelDB {
        val instanceConfig = LevelDBInstanceConfig(
            adapterRegistry = AdapterRegistry(mutableMapOf(Int::class to IntAdapter()))
        )
        return NativeLevelDB(DatabaseTestCase.createRandomDbPath().absolutePath, instanceConfig)
    }

    @Test
    fun `reader default overloads resolve and iterate`() {
        val db = newDb()

        db.putString("a", "1")
        db.putString("b", "2")

        assertEquals("1", db.getString("a"))
        assertEquals("2", db.getString("b", null))
        assertEquals("1", db.getBytes("a")?.decodeToString())
        assertEquals("1", db["a".encodeToByteArray()]?.decodeToString())

        val snapshot = db.obtainSnapshot()
        val iterator = db.iterator()
        iterator.seekToFirst()
        assertTrue(iterator.keyString() == "a" || iterator.keyString() == "b")
        iterator.close()
        snapshot.close()

        val snapshot2 = db.obtainSnapshot()
        val iterWithSnapshot = db.iterator(snapshot = snapshot2)
        iterWithSnapshot.seekToFirst()
        assertTrue(iterWithSnapshot.isValid)
        iterWithSnapshot.close()
        snapshot2.close()

        val propBytes = requireNotNull(db.getPropertyBytes("leveldb.stats".encodeToByteArray()))
        assertTrue(propBytes.isNotEmpty())
        assertEquals(propBytes.decodeToString(), db.getPropertyString("leveldb.stats"))

        db.close()
    }

    @Test
    fun `writer default overloads forward correctly`() {
        val db = newDb()

        db.putString("a", "1")
        db.putBytes("b", byteArrayOf(2))
        db.putBytes(byteArrayOf(10), byteArrayOf(11))
        db.putBytes(byteArrayOf(3), byteArrayOf(4))
        db.putValue("int", 5, Int::class)

        db.del("a")
        db.del("b", sync = true)
        db.del(byteArrayOf(3))
        db.del(byteArrayOf(10))

        assertNull(db.getString("a"))
        assertNull(db.getBytes("b"))
        assertNull(db.get(byteArrayOf(3)))
        assertNull(db.get(byteArrayOf(10)))
        assertEquals(5, db.getValue("int", Int::class))

        db.close()
    }
}
