package com.edwardstock.leveldb.test

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.common.PutGetDelWriteTest

/**
 * Runs the shared [PutGetDelWriteTest] suite against [MockLevelDB] so the in-memory
 * double is held to the same put/get/del/write + use-after-close contract as the native driver.
 */
class MockPutGetDelWriteTest : PutGetDelWriteTest() {
    @Throws(Exception::class)
    override fun obtainLevelDB(): LevelDB {
        return MockLevelDB(dbFile.toString())
    }
}
