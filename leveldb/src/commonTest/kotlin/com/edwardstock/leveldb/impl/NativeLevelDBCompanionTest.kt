package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.util.absolutePath
import kotlin.test.Test

class NativeLevelDBCompanionTest {
    @Test
    fun `companion destroy and repair run`() {
        val path = DatabaseTestCase.createRandomDbPath().absolutePath
        val db = NativeLevelDB(path)
        db.close()

        runCatching { NativeLevelDB.repair(path) }
        runCatching { NativeLevelDB.destroy(path) }
    }
}
