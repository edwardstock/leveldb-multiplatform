package com.edwardstock.leveldb.test

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.common.IterationTest

/**
 * Runs the shared [IterationTest] suite against [MockLevelDB] so the in-memory double's
 * iterator (stability under post-creation mutation, seekToLast/previous, key order,
 * not-valid/closed exceptions) matches the native driver.
 */
class MockIterationTest : IterationTest() {
    @Throws(Exception::class)
    override fun obtainLevelDB(): LevelDB {
        return MockLevelDB(dbFile.toString())
    }
}
