package com.edwardstock.leveldb.exception

import kotlin.test.Test
import kotlin.test.assertEquals

class ExceptionsTest {
    @Test
    fun `exceptions keep messages`() {
        val message = "boom"
        assertEquals(message, LevelDBClosedException(message).message)
        assertEquals(message, LevelDBCorruptionException(message).message)
        assertEquals(message, LevelDBIOException(message).message)
        assertEquals(message, LevelDBIteratorNotValidException(message).message)
        assertEquals(message, LevelDBNotFoundException(message).message)
        assertEquals(message, LevelDBSnapshotOwnershipException(message).message)
        val adapterMessage = LevelDBNoTypeAdapterException(String::class).message
        assertEquals("Cannot find converter for type kotlin.String", adapterMessage)
    }
}
