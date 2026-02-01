/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

@file:OptIn(ExperimentalStdlibApi::class)

package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.getBytes
import com.edwardstock.leveldb.api.getString
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.config.LevelDBDriverConfig
import com.edwardstock.leveldb.exception.LevelDBConcurrencyException
import com.edwardstock.leveldb.log.LevelDBConsoleLogger
import com.edwardstock.leveldb.util.stressConfig
import com.edwardstock.leveldb.utils.CoSynchronizedList
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LevelDBInstanceTest {

    private fun TestScope.newInstance(
        path: Path,
        driver: LevelDBDriverConfig = LevelDBDriverConfig(createIfMissing = true),
        scope: CoroutineScope? = null,
        configure: LevelDBInstance.Builder.() -> Unit = {},
    ) = LevelDBInstance.builder(path)
        .scope(scope ?: this)
        .instance {
            driver(driver)
        }
        .apply(configure)
        .build()

    @Test
    fun `parallel and nested contexts reuse single instance`() = runTest {
        val db = newInstance(DatabaseTestCase.createRandomDbPath())

        val job1 = launch {
            db.use { putString("x", "1"); getBytes("x") }
        }
        val job2 = launch {
            db.use {
                putString("y", "2")
                // accidental nested use
                db.use { getBytes("y") }
            }
        }
        job1.join()
        job2.join()

        val v1 = db.use { getString("x") }
        val v2 = db.use { getString("y") }
        assertEquals("1", v1)
        assertEquals("2", v2)
    }

    @Test
    fun `read and write helpers reuse use block`() = runTest {
        val db = newInstance(DatabaseTestCase.createRandomDbPath())
        db.use { putString("a", "1") }
        val v = db.use { getString("a") }
        assertEquals("1", v)
    }

    @Test
    fun `closeAndAwait closes idle DB and allows reopen`() = runTest {
        val db = newInstance(DatabaseTestCase.createRandomDbPath())
        db.use { putString("k", "v") }
        db.closeAndAwait()
        val v = db.use { getString("k") }
        assertEquals("v", v)
    }

    @Test
    fun `close schedules async close without breaking access`() = runTest {
        val db = newInstance(DatabaseTestCase.createRandomDbPath())
        db.use { putString("k", "v") }
        db.close()
        val v = db.use { getString("k") }
        assertEquals("v", v)
    }

    @Test
    fun `check nested calls of different methods`() = runTest {

        LevelDBInstance.builder("path")
            .instance { }


        val file = DatabaseTestCase.createRandomDbPath()
        val db = newInstance(file) {
            instance {
                logger(LevelDBConsoleLogger(true))
            }
        }

        assertFailsWith<LevelDBConcurrencyException>("useExclusively must not be called from LevelDBInstance.use()") {
            db.use {
                db.useExclusively { }
            }
        }
        assertFailsWith<LevelDBConcurrencyException>("Nested useExclusively() is forbidden for the same instance") {
            db.useExclusively {
                db.useExclusively {
                    open {
                        putString("k", "v")
                    }
                }
            }
        }
        assertFailsWith<LevelDBConcurrencyException>("use() is forbidden inside useExclusively()") {
            db.useExclusively {
                db.use { }
            }
        }

        // permitted
        db.use {
            db.use { }
        }

        // permitted
        db.useExclusively {
            open { }
        }

        assertFailsWith<LevelDBConcurrencyException>("Nested open() is forbidden in ExclusivePathOps") {
            db.useExclusively {
                open {
                    // makes no sense
                    open { }
                }
            }
        }

        db.use {
            yield()
            db.use { }
        }

        db.use {
            withContext(Dispatchers.Default) {
                db.use { }
            }
        }

        db.use {
            coroutineScope {
                launch {
                    db.use { }
                }
            }
        }
    }

    @Test
    fun `attempt to withPathExclusive after use does not fail`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val db = newInstance(file)

        db.use {}
        db.useExclusively { }
    }

    @Test
    fun `attempt to use inside useExclusively within a different scope fails`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val db = newInstance(file) {
            instance {
                logger(LevelDBConsoleLogger())
            }
        }


        assertFailsWith<LevelDBConcurrencyException> {
            db.useExclusively {
                it.launch {
                    db.use {}
                }
            }
        }

        assertFailsWith<LevelDBConcurrencyException> {
            db.useExclusively {
                val jobs = mutableListOf<Job>()
                jobs += it.launch {
                    db.use {
                        db.use {}
                    }
                }
                jobs += it.launch {
                    db.use {}
                }
                jobs.joinAll()
            }
        }

        assertFailsWith<TimeoutCancellationException> {
            val scope: TestScope = this
            withTimeout(2.seconds) {
                db.useExclusively {
                    val job = scope.launch {
                        db.use {}
                    }
                    // guaranteed deadlock
                    job.join()
                }
            }
        }
    }

    @Test
    fun `reentrant nested use does not deadlock and does not extend lifetime`() = runTest {
        repeat(10) {
            val file = DatabaseTestCase.createRandomDbPath()
            val db = newInstance(file)

            val enteredExclusive = Channel<Unit>(capacity = 1)
            val leaseEntered = CompletableDeferred<Unit>()
            val outerMayFinish = Channel<Unit>(capacity = 1)

            val outer = launch {
                db.use {
                    leaseEntered.complete(Unit)
                    putString("k", "v0")
                    putString("k", "v1")

                    outerMayFinish.send(Unit) // allow main to release us

                    // nested use should not deadlock
                    db.use { getBytes("k") }
                }
            }

            val exclusive = launch {
                leaseEntered.await()
                // signal we are about to attempt exclusivity (will block on refCount>0)
                db.useExclusively {
                    enteredExclusive.trySend(Unit) // we got exclusivity
                }
            }

            // let outer finish the write (and release the lease)
            outerMayFinish.receive()
            outer.join()
            exclusive.join()

            // After exclusive, DB still usable and value is last write
            val v = db.use { getString("k") }
            assertEquals("v1", v)
            assertTrue(enteredExclusive.tryReceive().isSuccess)
        }
    }

    @Test
    fun `withPathExclusive waits for active leases then runs block`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val db = newInstance(file)

        val stages = CoSynchronizedList<String>()

        val useEntered = CompletableDeferred<Unit>()// exclusive -> main

        // Hold a lease open until we say so
        val holder = launch {
            db.use {
                useEntered.complete(Unit)
                stages += "lease-active"
            }
        }

        // Start exclusive only after the lease is actually acquired
        val exclusive = launch {
            useEntered.await()
            db.useExclusively {
                stages += "exclusive-enter"
            }
            stages += "exclusive-exit"
        }

        holder.join()
        exclusive.join()

        assertEquals(listOf("lease-active", "exclusive-enter", "exclusive-exit"), stages.toList())
    }

    @Test
    fun `exceptions inside write do not poison subsequent ops`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val db = newInstance(file)

        runCatching {
            db.use {
                putString("boom", "1")
                error("mid-failure")
            }
        }
        val v = db.use { getString("boom") }
        assertEquals("1", v)
    }

    @Test
    fun `two instances same path share one native DB`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val a = newInstance(file)
        val b = newInstance(file)

        a.use { putString("z", "9") }
        val fromB = b.use { getString("z") }
        assertEquals("9", fromB)
    }

    val ok = atomic(0)

    @Test
    fun `stress many short writes then reads`() {
        ok.value = 0

        runTest {
            val jobs = mutableListOf<Job>()
            val file = DatabaseTestCase.createRandomDbPath()
            val db = newInstance(file)

            val keys = (0 until 300).map { "k$it" }
            coroutineScope {
                keys.chunked(20).forEach { chunk ->
                    jobs += launch {
                        db.use {
                            chunk.forEach { k -> putString(k, k) }
                        }

                    }
                }
            }

            coroutineScope {
                keys.chunked(20).forEach { chunk ->
                    jobs += launch {
                        chunk.forEach { k ->
                            db.use {
                                val v = getString(k)
                                if (k == v) ok.incrementAndGet()
                            }
                        }
                    }
                }
            }

            jobs.joinAll()
            assertEquals(keys.size, ok.value)
        }
    }

    @Test
    fun `stress concurrent use and exclusive access`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val db = newInstance(file)
        val errors = atomic(0)
        val firstError = atomic<Throwable?>(null)
        val exclusiveRequested = atomic(false)
        val activeWorkers = atomic(0)
        val config = stressConfig()

        coroutineScope {
            val workers = (0 until config.workers).map { workerId ->
                launch {
                    repeat(config.iterations) { idx ->
                        while (exclusiveRequested.value) {
                            yield()
                        }
                        runCatching {
                            activeWorkers.incrementAndGet()
                            db.use {
                                if ((idx + workerId) % 2 == 0) {
                                    putString("k$idx", "v$idx")
                                } else {
                                    getBytes("k$idx")
                                }
                            }
                        }.onFailure { err ->
                            firstError.compareAndSet(null, err)
                            errors.incrementAndGet()
                        }.also {
                            activeWorkers.decrementAndGet()
                        }
                    }
                }
            }

            val exclusive = launch {
                repeat(config.exclusiveRepeats) {
                    exclusiveRequested.value = true
                    while (activeWorkers.value > 0) {
                        yield()
                    }
                    db.useExclusively {
                        // Must not call db.use() while exclusive lock is held
                        // Exclusive block is for out-of-band work like compaction or cleanup
                    }
                    exclusiveRequested.value = false
                }
            }

            workers.joinAll()
            exclusive.join()
        }

        firstError.value?.let { throw it }
        assertEquals(0, errors.value)
    }
}
