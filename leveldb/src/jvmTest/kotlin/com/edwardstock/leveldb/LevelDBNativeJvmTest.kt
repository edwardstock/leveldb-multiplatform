package com.edwardstock.leveldb

import com.edwardstock.leveldb.internal.LevelDBNativeProvider
import com.edwardstock.leveldb.internal.NULL_DB_BATCH_HANDLE
import com.edwardstock.leveldb.internal.NULL_DB_HANDLE
import com.edwardstock.leveldb.internal.NULL_DB_ITERATOR_HANDLE
import com.edwardstock.leveldb.internal.NULL_DB_SNAPSHOT_HANDLE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LevelDBNativeJvmTest {
    @Test
    fun `null handles are zero and provider is self`() {
        assertEquals(0L, NULL_DB_HANDLE)
        assertEquals(0L, NULL_DB_BATCH_HANDLE)
        assertEquals(0L, NULL_DB_ITERATOR_HANDLE)
        assertEquals(0L, NULL_DB_SNAPSHOT_HANDLE)
        assertSame(LevelDBNativeProvider, LevelDBNativeProvider.impl)
    }
}
