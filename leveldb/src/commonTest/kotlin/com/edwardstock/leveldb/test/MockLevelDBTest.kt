package com.edwardstock.leveldb.test

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.get
import com.edwardstock.leveldb.api.getBytes
import com.edwardstock.leveldb.api.putBytes
import com.edwardstock.leveldb.exception.LevelDBClosedException
import com.edwardstock.leveldb.exception.LevelDBIteratorNotValidException
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
                byteArrayOf(0, 0, 4) to byteArrayOf(4),
                byteArrayOf(0, 0, 3) to byteArrayOf(3),
            )
        )

        db.iterator().use { it ->
            it.seekToFirst()
            val k1 = it.key().contentToString()
            it.next()
            val k2 = it.key().contentToString()
            it.next()
            val k3 = it.key().contentToString()

            assertEquals(
                expected = "[0, 0, 2]",
                actual = k1,
                message = "Unexpected first key"
            )
            assertEquals(
                expected = "[0, 0, 3]",
                actual = k2,
                message = "Unexpected second key"
            )
            assertEquals(
                expected = "[0, 0, 4]",
                actual = k3,
                message = "Unexpected third key"
            )

            // Seek lands on the first key >= requested.
            it.seek(byteArrayOf(0, 0, 3))
            assertContentEquals(byteArrayOf(0, 0, 3), it.key())
            assertContentEquals(byteArrayOf(3), it.value())
            it.next()

            // After 3 comes 4.
            assertContentEquals(byteArrayOf(0, 0, 4), it.key())

            // Next after the last element makes iterator invalid; key() must throw.
            it.next()
            assertFailsWith<LevelDBIteratorNotValidException> { it.key() }
        }
    }

    @Test
    fun `isValid throws after close`() {
        val db = MockLevelDB()
        val it = db.iterator()
        it.close()
        // After close, isValid must reject access with the concrete closed-db exception,
        // not just any Exception. Catches a production regression where the iterator's
        // isValid getter stops calling ensureOpen() (returning a stale boolean instead).
        assertFailsWith<LevelDBClosedException> { it.isValid }
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

    @Test
    fun `instanced test`() = runTest {
        val instance = LevelDBInstance.builder("fake") {
            instance {
                dbFactory { path, config -> MockLevelDB(path, config) }
                fileSystem(FakeFileSystem())
            }
        }

        instance.use {
            putBytes("k".encodeToByteArray(), "v1".encodeToByteArray())
            val snap = obtainSnapshot()
            putBytes("k".encodeToByteArray(), "v2".encodeToByteArray())
            assertContentEquals("v1".encodeToByteArray(), getBytes("k".encodeToByteArray(), snap))
            snap.close()
        }

        instance.closeAndAwait()
    }

    @Test
    fun `multiple instances test`() = runTest {
        val initialData = mapOf(
            "init".encodeToByteArray() to "value".encodeToByteArray()
        )
        val instance = LevelDBInstance.mock(initialData = initialData)

        instance.use {
            assertEquals("value", getBytes("init")?.decodeToString())
            putBytes("k", "v1".encodeToByteArray())
        }

        instance.use {
            val value = getBytes("k")
            assertEquals("v1", value?.decodeToString())
        }

        instance.closeAndAwait()
    }
}
