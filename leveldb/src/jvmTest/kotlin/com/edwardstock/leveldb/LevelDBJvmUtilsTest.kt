package com.edwardstock.leveldb

import kotlin.test.Test

class LevelDBJvmUtilsTest {
    @Test
    fun `loadNative does not throw`() {
        LevelDB.loadNative()
    }

    @Test
    fun `system load does not throw`() {
        val libPath = System.getProperty("leveldb.jni.path") ?: return
        System.load(libPath)
    }
}
