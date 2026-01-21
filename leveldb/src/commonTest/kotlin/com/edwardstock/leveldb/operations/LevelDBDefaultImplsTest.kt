package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.LevelDBConfig
import com.edwardstock.leveldb.ValueAdapter
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.implementation.NativeLevelDB
import com.edwardstock.leveldb.util.absolutePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelDBDefaultImplsTest {
    private class IntAdapter : ValueAdapter<Int> {
        override fun decode(key: ByteArray, value: ByteArray?): Int? {
            return value?.let { 5 }
        }

        override fun encode(value: Int): ByteArray {
            return value.toString().encodeToByteArray()
        }
    }

    private fun newDb(): NativeLevelDB {
        val config = LevelDBConfig(
            createIfMissing = true,
            adapters = mutableMapOf(Int::class to IntAdapter())
        )
        return NativeLevelDB(DatabaseTestCase.createRandomDbPath().absolutePath, config)
    }

    @Test
    fun `reader default overloads resolve and iterate`() {
        val db = newDb()
        val writer: LevelDBWriter = LevelDBWriterImpl(db)
        val reader: LevelDBReader = LevelDBReaderImpl(db)

        writer.put("a", "1")
        writer.put("b", "2")

        assertEquals("1", reader.getString("a"))
        assertEquals("2", reader.getString("b", null))
        assertEquals("1", reader.get("a")?.decodeToString())
        assertEquals("1", reader.get("a".encodeToByteArray())?.decodeToString())

        val snapshot = reader.obtainSnapshot()
        val iterator = reader.iterator()
        iterator.seekToFirst()
        assertTrue(iterator.keyString() == "a" || iterator.keyString() == "b")
        iterator.close()
        snapshot.close()

        val snapshot2 = reader.obtainSnapshot()
        val iterWithSnapshot = reader.iterator(snapshot = snapshot2)
        iterWithSnapshot.seekToFirst()
        assertTrue(iterWithSnapshot.isValid)
        iterWithSnapshot.close()
        snapshot2.close()

        val propBytes = requireNotNull(reader.getPropertyBytes("leveldb.stats".encodeToByteArray()))
        assertTrue(propBytes.isNotEmpty())
        assertEquals(propBytes.decodeToString(), reader.getProperty("leveldb.stats"))

        db.close()
    }

    @Test
    fun `writer default overloads forward correctly`() {
        val db = newDb()
        val writer: LevelDBWriter = LevelDBWriterImpl(db)
        val reader: LevelDBReader = LevelDBReaderImpl(db)

        writer.put("a", "1")
        writer.put("b", byteArrayOf(2))
        writer.put(byteArrayOf(10), byteArrayOf(11))
        writer.put(byteArrayOf(3), byteArrayOf(4))
        writer.put("int", 5, Int::class)

        writer.del("a")
        writer.del("b", sync = true)
        writer.del(byteArrayOf(3))
        writer.del(byteArrayOf(10))

        assertNull(reader.getString("a"))
        assertNull(reader.get("b"))
        assertNull(reader.get(byteArrayOf(3)))
        assertNull(reader.get(byteArrayOf(10)))
        assertEquals(5, reader.get("int", Int::class))

        db.close()
    }
}
