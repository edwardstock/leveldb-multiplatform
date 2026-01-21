package com.edwardstock.leveldb.operations

import com.edwardstock.leveldb.LevelDBConfig
import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.common.DatabaseTestCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.edwardstock.leveldb.exception.LevelDBNotFoundException
import kotlin.test.assertTrue

class LevelDBOpsDefaultsTest {
    private fun newInstance() = LevelDBInstance(
        path = DatabaseTestCase.createRandomDbPath(),
        config = LevelDBConfig(createIfMissing = true)
    )

    @Test
    fun `reader defaults handle snapshots and string helpers`() = runTest {
        val db = newInstance()

        db.write { put("k", "v1") }

        val (v1, v2, propertyResult) = db.use {
            val snapshot = read { obtainSnapshot() }
            write { put("k", "v2") }
            val fromSnapshot = read { getString("k", snapshot) }
            val current = read { getString("k") }
            val property = runCatching { read { getProperty("unknown.property") } }
            snapshot.close()
            Triple(fromSnapshot, current, property)
        }

        assertEquals("v1", v1)
        assertEquals("v2", v2)
        assertTrue(propertyResult.isFailure)
        assertTrue(propertyResult.exceptionOrNull() is LevelDBNotFoundException)
        db.closeAndAwait()
    }

    @Test
    fun `reader iterator overloads return values`() = runTest {
        val db = newInstance()

        db.write {
            put("a", "1")
            put("b", "2")
        }

        val (firstKey, firstValue) = db.read {
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

        db.write {
            put("a", "1")
            put("b", "2".encodeToByteArray())
            del("a")
            withBatch {
                put("c".encodeToByteArray(), "3".encodeToByteArray())
                del("b".encodeToByteArray())
            }
        }

        val a = db.read { getString("a") }
        val b = db.read { getString("b") }
        val c = db.read { getString("c") }

        assertNull(a)
        assertNull(b)
        assertEquals("3", c)

        db.closeAndAwait()
    }
}
