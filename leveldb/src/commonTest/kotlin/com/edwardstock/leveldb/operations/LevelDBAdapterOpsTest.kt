package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.AdapterRegistry
import com.edwardstock.leveldb.api.ValueAdapter
import com.edwardstock.leveldb.api.getValue
import com.edwardstock.leveldb.api.putBytes
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.api.putValue
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.exception.LevelDBNoTypeAdapterException
import com.edwardstock.leveldb.impl.NativeLevelDB
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

        override fun decode(value: ByteArray): Int {
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
        val config = LevelDBInstanceConfig(
            adapterRegistry = AdapterRegistry(mutableMapOf(Int::class to adapter))
        )
        return NativeLevelDB(
            DatabaseTestCase.createRandomDbPath().absolutePath,
            config,
        ) to adapter
    }

    @Test
    fun `writer and reader use adapters with KClass`() {
        val (db, adapter) = newDb()

        db.putValue("int", 123, Int::class)
        val result = db.getValue("int", Int::class)

        assertEquals(123, result)
        assertTrue(adapter.encodeCalled)
        assertTrue(adapter.decodeCalled)
        db.close()
    }

    @Test
    fun `reader throws on missing adapter for existing value`() {
        val (db) = newDb()

        db.putString("value", "42")

        assertFailsWith<LevelDBNoTypeAdapterException> {
            db.getValue("value", Double::class)
        }
        db.close()
    }

    @Test
    fun `writer and reader reified helpers use adapters`() {
        val (db, adapter) = newDb()

        db.putValue("int", 7)
        assertEquals(123, db.getValue<Int>("int"))
        assertTrue(adapter.encodeCalled)
        assertTrue(adapter.decodeCalled)

        db.putBytes("int", null)
        assertNull(db.getValue<Int>("int"))

        db.close()
    }
}
