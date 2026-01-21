package com.edwardstock.leveldb

import kotlin.test.Test

class LevelDBJvmUtilsTest {
    @Test
    fun `loadNative does not throw`() {
        LevelDB.loadNative()
    }
}
