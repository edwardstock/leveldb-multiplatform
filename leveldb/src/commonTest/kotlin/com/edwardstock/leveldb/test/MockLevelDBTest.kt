package com.edwardstock.leveldb.test

import com.edwardstock.leveldb.api.get
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MockLevelDBTest {
    @Test
    fun `putBytes with null deletes`() {
        val db = MockLevelDB()
        db.putBytes("a".encodeToByteArray(), "1".encodeToByteArray())
        db.putBytes("a".encodeToByteArray(), null)
        assertNull(db["a"])
    }

    @Test
    fun `getPropertyBytes always null`() {
        val db = MockLevelDB()
        assertNull(db.getPropertyBytes("anything".encodeToByteArray()))
    }

    @Test
    fun `iterator seek and order are lexicographic`() {
        val db = MockLevelDB(
            initial = mapOf(
                byteArrayOf(0, 0, 2) to byteArrayOf(2),
                byteArrayOf(0, 0, 10) to byteArrayOf(10),
                byteArrayOf(0, 0, 3) to byteArrayOf(3),
            )
        )

        db.iterator().use { it ->
            it.seek(byteArrayOf(0, 0, 3))
            assertContentEquals(byteArrayOf(0, 0, 3), it.key())
            assertContentEquals(byteArrayOf(3), it.value())
            it.next()
            assertContentEquals(byteArrayOf(0, 0, 10), it.key())
        }
    }

    @Test
    fun `isValid throws after close`() {
        val db = MockLevelDB()
        val it = db.iterator()
        it.close()
        assertFailsWith<Exception> { it.isValid }
    }

    @Test
    fun `snapshot is isolated`() {
        val db = MockLevelDB()
        db.putBytes("k".encodeToByteArray(), "v1".encodeToByteArray())
        val snap = db.obtainSnapshot()
        db.putBytes("k".encodeToByteArray(), "v2".encodeToByteArray())
        assertContentEquals("v1".encodeToByteArray(), db.getBytes("k".encodeToByteArray(), snap))
        snap.close()
    }
}
