/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

@file:OptIn(ExperimentalStdlibApi::class)

package com.edwardstock.leveldb.implementation

import com.edwardstock.leveldb.LevelDBConfig
import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.util.stressConfig
import com.edwardstock.leveldb.utils.CoSynchronizedList
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelDBInstanceTest {

    private fun newInstance(path: Path) = LevelDBInstance(
        path = path,
        config = LevelDBConfig(createIfMissing = true)
    )

    @Test
    fun `parallel and nested contexts reuse single instance`() = runTest {
        val db = newInstance(DatabaseTestCase.createRandomDbPath())

        val job1 = launch {
            db.use { write { put("x", "1"); get("x") } }
        }
        val job2 = launch {
            db.use {
                write {
                    put("y", "2")
                }
                // accidental nested use
                db.use { read { get("y") } }
            }
        }
        job1.join()
        job2.join()

        val v1 = db.use { read { getString("x") } }
        val v2 = db.use { read { getString("y") } }
        assertEquals("1", v1)
        assertEquals("2", v2)
    }

    @Test
    fun `read and write helpers reuse use block`() = runTest {
        val db = newInstance(DatabaseTestCase.createRandomDbPath())
        db.write { put("a", "1") }
        val v = db.read { getString("a") }
        assertEquals("1", v)
    }

    @Test
    fun `closeAndAwait closes idle DB and allows reopen`() = runTest {
        val db = newInstance(DatabaseTestCase.createRandomDbPath())
        db.write { put("k", "v") }
        db.closeAndAwait()
        val v = db.read { getString("k") }
        assertEquals("v", v)
    }

    @Test
    fun `close schedules async close without breaking access`() = runTest {
        val db = newInstance(DatabaseTestCase.createRandomDbPath())
        db.write { put("k", "v") }
        db.close()
        val v = db.read { getString("k") }
        assertEquals("v", v)
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
                    write {
                        put("k", "v0")
                        put("k", "v1")
                    }

                    outerMayFinish.send(Unit) // allow main to release us

                    // nested use should not deadlock
                    db.use { read { get("k") } }
                }
            }

            val exclusive = launch {
                leaseEntered.await()
                // signal we are about to attempt exclusivity (will block on refCount>0)
                LevelDBInstance.withPathExclusive(file) {
                    enteredExclusive.trySend(Unit) // we got exclusivity
                }
            }

            // let outer finish the write (and release the lease)
            outerMayFinish.receive()
            outer.join()
            exclusive.join()

            // After exclusive, DB still usable and value is last write
            val v = db.use { read { getString("k") } }
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
            LevelDBInstance.withPathExclusive(file) {
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
                write {
                    put("boom", "1")
                    error("mid-failure")
                }
            }
        }
        val v = db.use { read { getString("boom") } }
        assertEquals("1", v)
    }

    @Test
    fun `two instances same path share one native DB`() = runTest {
        val file = DatabaseTestCase.createRandomDbPath()
        val a = newInstance(file)
        val b = newInstance(file)

        a.use { write { put("z", "9") } }
        val fromB = b.use { read { getString("z") } }
        assertEquals("9", fromB)
    }

    val ok = atomic(0)

    @Test
    fun `stress many short writes then reads`() {
        ok.value = 0

        runTest {
            val jobs = mutableListOf<kotlinx.coroutines.Job>()
            val file = DatabaseTestCase.createRandomDbPath()
            val db = newInstance(file)

            val keys = (0 until 300).map { "k$it" }
            coroutineScope {
                keys.chunked(20).forEach { chunk ->
                    jobs += launch {
                        db.use {
                            write {
                                chunk.forEach { k -> put(k, k) }
                            }
                        }

                    }
                }
            }

            coroutineScope {
                keys.chunked(20).forEach { chunk ->
                    jobs += launch {
                        chunk.forEach { k ->
                            db.use {
                                read {
                                    val v = getString(k)
                                    if (k == v) ok.incrementAndGet()
                                }
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
                            kotlinx.coroutines.yield()
                        }
                        runCatching {
                            activeWorkers.incrementAndGet()
                            db.use {
                                if ((idx + workerId) % 2 == 0) {
                                    write { put("k$idx", "v$idx") }
                                } else {
                                    read { get("k$idx") }
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
                        kotlinx.coroutines.yield()
                    }
                    LevelDBInstance.withPathExclusive(file) {
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
