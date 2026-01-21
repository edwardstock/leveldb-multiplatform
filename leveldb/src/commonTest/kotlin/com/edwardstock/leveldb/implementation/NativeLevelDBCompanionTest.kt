package com.edwardstock.leveldb.implementation

import com.edwardstock.leveldb.LevelDBConfig
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.util.absolutePath
import kotlin.test.Test

class NativeLevelDBCompanionTest {
    @Test
    fun `companion destroy and repair run`() {
        val path = DatabaseTestCase.createRandomDbPath().absolutePath
        val db = NativeLevelDB(path, LevelDBConfig(createIfMissing = true))
        db.close()

        runCatching { NativeLevelDB.repair(path) }
        runCatching { NativeLevelDB.destroy(path) }
    }
}
