package com.edwardstock.leveldb.utils

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.LevelDBConfig
import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.common.DatabaseTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsExtensionsTest {
    @Test
    fun `forEach extensions iterate keys and values`() = kotlinx.coroutines.test.runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val instance = LevelDBInstance(path, LevelDBConfig(createIfMissing = true))

        instance.write {
            put("a", "1")
            put("b", "2")
        }

        val readerAll = mutableListOf<Pair<String, String>>()
        val readerKeys = mutableListOf<String>()
        val readerValues = mutableListOf<String>()

        instance.read {
            forEachAll { k, v -> readerAll += k to v }
            forEachKeys { readerKeys += it }
            forEachValues { readerValues += it }
        }

        val db = LevelDB.open(path.toString()) { createIfMissing = true }
        val dbAll = mutableListOf<Pair<String, String>>()
        val dbKeys = mutableListOf<String>()
        val dbValues = mutableListOf<String>()

        db.forEachAll { k, v -> dbAll += k to v }
        db.forEachKeys { dbKeys += it }
        db.forEachValues { dbValues += it }
        db.close()

        assertEquals(setOf("a", "b"), readerKeys.toSet())
        assertEquals(setOf("1", "2"), readerValues.toSet())
        assertEquals(setOf("a", "b"), dbKeys.toSet())
        assertEquals(setOf("1", "2"), dbValues.toSet())
        assertEquals(readerAll.toSet(), dbAll.toSet())

        instance.closeAndAwait()
    }
}
