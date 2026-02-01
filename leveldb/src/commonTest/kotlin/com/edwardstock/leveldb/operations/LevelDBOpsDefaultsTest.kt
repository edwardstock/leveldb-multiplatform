package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.del
import com.edwardstock.leveldb.api.getPropertyString
import com.edwardstock.leveldb.api.getString
import com.edwardstock.leveldb.api.putBytes
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.config.LevelDBDriverConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelDBOpsDefaultsTest {
    private fun newInstance() = LevelDBInstance.builder(DatabaseTestCase.createRandomDbPath())
        .driver { LevelDBDriverConfig(createIfMissing = true) }
        .build()

    @Test
    fun `reader defaults handle snapshots and string helpers`() = runTest {
        val db = newInstance()

        db.use { putString("k", "v1") }

        val (v1, v2, propertyResult) = db.use {
            val snapshot = obtainSnapshot()
            putString("k", "v2")
            val fromSnapshot = getString("k", snapshot)
            val current = getString("k")
            val property = getPropertyString("unknown.property")
            snapshot.close()
            Triple(fromSnapshot, current, property)
        }

        assertEquals("v1", v1)
        assertEquals("v2", v2)
        assertNull(propertyResult)
        db.closeAndAwait()
    }

    @Test
    fun `reader iterator overloads return values`() = runTest {
        val db = newInstance()

        db.use {
            putString("a", "1")
            putString("b", "2")
        }

        val (firstKey, firstValue) = db.use {
            val it = iterator()
            it.seekToFirst()
            val key = it.keyString()
            val value = it.valueString()
            it.close()
            key to value
        }

        assertTrue(firstKey == "a" || firstKey == "b")
        assertTrue(firstValue == "1" || firstValue == "2")

        db.closeAndAwait()
    }

    @Test
    fun `writer defaults handle string overloads and batch`() = runTest {
        val db = newInstance()

        db.use {
            putString("a", "1")
            putBytes("b", "2".encodeToByteArray())
            del("a")
            withBatch {
                put("c".encodeToByteArray(), "3".encodeToByteArray())
                del("b".encodeToByteArray())
            }
        }

        val a = db.use { getString("a") }
        val b = db.use { getString("b") }
        val c = db.use { getString("c") }

        assertNull(a)
        assertNull(b)
        assertEquals("3", c)

        db.closeAndAwait()
    }
}
