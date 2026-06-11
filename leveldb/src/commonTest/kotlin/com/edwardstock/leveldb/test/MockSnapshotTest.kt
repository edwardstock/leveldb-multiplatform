package com.edwardstock.leveldb.test

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.common.SnapshotTest

/**
 * Runs the shared [SnapshotTest] suite against [MockLevelDB] so the in-memory double's
 * point-in-time snapshot semantics match the native driver.
 */
class MockSnapshotTest : SnapshotTest() {
    @Throws(Exception::class)
    override fun obtainLevelDB(): LevelDB {
        return MockLevelDB(dbFile.toString())
    }
}
