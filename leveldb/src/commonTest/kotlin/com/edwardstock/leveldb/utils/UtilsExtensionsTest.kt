package com.edwardstock.leveldb.utils

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.forEachAll
import com.edwardstock.leveldb.api.forEachAllKeyString
import com.edwardstock.leveldb.api.forEachAllValueString
import com.edwardstock.leveldb.api.open
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.config.LevelDBDriverConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsExtensionsTest {
    @Test
    fun `forEach extensions iterate keys and values`() = kotlinx.coroutines.test.runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val instance = LevelDBInstance.builder(path)
            .scope(this)
            .driver { LevelDBDriverConfig(createIfMissing = true) }
            .build()

        instance.use {
            putString("a", "1")
            putString("b", "2")
        }

        val readerAll = mutableListOf<Pair<String, String>>()
        val readerKeys = mutableListOf<String>()
        val readerValues = mutableListOf<String>()

        instance.use {
            forEachAll { readerAll += it.keyString() to it.valueString() }
            forEachAllKeyString { readerKeys += it }
            forEachAllValueString { readerValues += it }
        }

        val db = LevelDB.open(path.toString())
        val dbAll = mutableListOf<Pair<String, String>>()
        val dbKeys = mutableListOf<String>()
        val dbValues = mutableListOf<String>()

        db.forEachAll { dbAll += it.keyString() to it.valueString() }
        db.forEachAllKeyString { dbKeys += it }
        db.forEachAllValueString { dbValues += it }
        db.close()

        assertEquals(setOf("a", "b"), readerKeys.toSet())
        assertEquals(setOf("1", "2"), readerValues.toSet())
        assertEquals(setOf("a", "b"), dbKeys.toSet())
        assertEquals(setOf("1", "2"), dbValues.toSet())
        assertEquals(readerAll.toSet(), dbAll.toSet())

        instance.closeAndAwait()
    }
}
