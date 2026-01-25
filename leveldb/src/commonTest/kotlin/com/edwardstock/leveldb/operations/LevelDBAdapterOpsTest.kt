package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.LevelDBConfig
import com.edwardstock.leveldb.ValueAdapter
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.exception.LevelDBNoTypeAdapterException
import com.edwardstock.leveldb.implementation.NativeLevelDB
import com.edwardstock.leveldb.util.absolutePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelDBAdapterOpsTest {
    private class TrackingAdapter : ValueAdapter<Int> {
        var encodeCalled = false
        var decodeCalled = false

        override fun decode(key: ByteArray, value: ByteArray?): Int? {
            decodeCalled = true
            return 123
        }

        override fun encode(value: Int): ByteArray {
            encodeCalled = true
            return byteArrayOf(1, 2, 3)
        }
    }

    private fun newDb(): Pair<NativeLevelDB, TrackingAdapter> {
        val adapter = TrackingAdapter()
        val config = LevelDBConfig(
            createIfMissing = true,
            adapters = mutableMapOf(Int::class to adapter)
        )
        return NativeLevelDB(DatabaseTestCase.createRandomDbPath().absolutePath, config) to adapter
    }

    @Test
    fun `writer and reader use adapters with KClass`() {
        val (db, adapter) = newDb()
        val writer = LevelDBOpsImpl(db)
        val reader = LevelDBOpsImpl(db)

        writer.put("int", 123, Int::class)
        val result = reader.get("int", Int::class)

        assertEquals(123, result)
        assertTrue(adapter.encodeCalled)
        assertTrue(adapter.decodeCalled)
        db.close()
    }

    @Test
    fun `reader throws on missing adapter for existing value`() {
        val (db) = newDb()
        val writer = LevelDBOpsImpl(db)
        val reader = LevelDBOpsImpl(db)

        writer.put("value", "42")

        assertFailsWith<LevelDBNoTypeAdapterException> {
            reader.get("value", Double::class)
        }
        db.close()
    }

    @Test
    fun `writer and reader reified helpers use adapters`() {
        val (db, adapter) = newDb()
        val writer = LevelDBOpsImpl(db)
        val reader = LevelDBOpsImpl(db)

        writer.put("int", 7)
        assertEquals(123, reader.getAny<Int>("int"))
        assertTrue(adapter.encodeCalled)
        assertTrue(adapter.decodeCalled)

        writer.put("int", null)
        assertNull(reader.getAny<Int>("int"))

        db.close()
    }
}
