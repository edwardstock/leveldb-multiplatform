/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.getString
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.currentSchemaVersion
import com.edwardstock.leveldb.exception.LevelDBMigrationException
import com.edwardstock.leveldb.internal.NumberByteArrayConverter
import com.edwardstock.leveldb.log.LevelDBConsoleLogger
import com.edwardstock.leveldb.migration.LevelDBMigration
import com.edwardstock.leveldb.migration.LevelDBMigrationSafetyPolicy
import com.edwardstock.leveldb.migration.LevelDBSchema
import com.edwardstock.leveldb.migration.LevelDBSchemaVersion
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LevelDBMigrationTest {

    @Test
    fun `migration applies linear chain and leaves metadata clean`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executedSteps = mutableListOf<String>()

        val migrations = listOf(
            RecordingMigration(0, 1, "m0_1", executedSteps),
            RecordingMigration(1, 2, "m1_2", executedSteps),
            RecordingMigration(2, 3, "m2_3", executedSteps),
            // можно хоть дальше, но target=3
        )

        val schema = LevelDBSchema(
            targetVersion = 3,
            migrations = migrations,
        )
        val instance = migrationInstance(path, schema)

        try {
            instance.use { putString("post-migration", "ready") }

            assertEquals(listOf("m0_1", "m1_2", "m2_3"), executedSteps)

            val storedVersion = instance.use {
                val raw = requireNotNull(getBytes(schema.versionKey))
                NumberByteArrayConverter.decode(raw, 4).toInt()
            }
            assertEquals(3, storedVersion)

            assertEquals("ready", instance.use { getString("post-migration") })

            // must be removed after successful migration
            assertNull(instance.use { getBytes(schema.inProgressKey) })
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `migration applies linear chain and leaves metadata clean when migrateIfNeeded called`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executedSteps = mutableListOf<String>()

        val migrations = listOf(
            RecordingMigration(0, 1, "m0_1", executedSteps),
            RecordingMigration(1, 2, "m1_2", executedSteps),
            RecordingMigration(2, 3, "m2_3", executedSteps),
        )

        val schema = LevelDBSchema(
            targetVersion = 3,
            migrations = migrations,
            migrateAutomatically = false
        )
        val instance = migrationInstance(path, schema)

        try {
            // does not fail, but does not apply migration
            instance.use { putString("pre-migration", "ready") }
            assertEquals("ready", instance.use { getString("pre-migration") })
            assertTrue("no migration steps are executed") { executedSteps.isEmpty() }

            // migrate manually
            instance.migrateIfNeeded()

            instance.use { putString("post-migration", "ready") }

            assertEquals(listOf("m0_1", "m1_2", "m2_3"), executedSteps)

            val storedVersion = instance.use {
                val raw = requireNotNull(getBytes(schema.versionKey))
                NumberByteArrayConverter.decode(raw, 4).toInt()
            }
            assertEquals(3, storedVersion)

            assertEquals("ready", instance.use { getString("post-migration") })

            // must be removed after successful migration
            assertNull(instance.use { getBytes(schema.inProgressKey) })
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `concurrent use triggers only one migration attempt`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()

        val failedMigrationRan = atomic<Int>(0)
        val schema = LevelDBSchema(
            targetVersion = 2,
            revision = 0,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                RecordingMigration(0, 1, "m0_1", mutableListOf()),
                FailingMigration(1, 2, "m0_2", mutableListOf(), 1000) {
                    failedMigrationRan.value += 1
                },
            )
        )

        val instance = LevelDBInstance.builder(path)
            .fileSystem(FakeFileSystem())
            .scope(this)
            .schema(schema)
            .instance {
                logger(LevelDBConsoleLogger())
            }
            .build()

        val jobs = mutableListOf<Job>()
        val gate = CompletableDeferred<Unit>()
        repeat(3) {
            jobs += launch {
                gate.await()
                assertFailsWith<LevelDBMigrationException> { instance.use {} }
            }
        }
        gate.complete(Unit)

        jobs.joinAll()
        assertNotNull(instance.state.schemaFailedState)
        assertEquals(1, failedMigrationRan.value)
    }

    @Test
    fun `missing linear step must fail and must not mark schemaOkForVersion as target`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()

        val schema = LevelDBSchema(
            targetVersion = 2,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                RecordingMigration(0, 1, "m0_1", mutableListOf()),
                // no migration to v2
            )
        )

        val instance = migrationInstance(path, schema)

        try {
            repeat(3) {
                withTimeout(3_000) {
                    assertFailsWith<LevelDBMigrationException> {
                        instance.use {
                            putString("x", "y")
                        }
                    }
//                    println("Failure: $fail")
                }
            }

            val storedVersion = instance.currentSchemaVersion
            assertTrue(storedVersion < 2)

            withTimeout(3.seconds) {
                assertFails {
                    instance.useExclusively {
                        open {
                            putString("after", "ok")
                        }
                    }
                }
            }
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    private fun TestScope.migrationInstance(
        path: Path,
        schema: LevelDBSchema,
        fileSystem: FileSystem = FileSystem.SYSTEM,
    ): LevelDBInstance {
        return LevelDBInstance.builder(path)
            .fileSystem(fileSystem)
            .scope(this)
            .schema(schema)
            .instance {
                logger(LevelDBConsoleLogger())
            }
            .build()
    }

}

// -------------------- migrations used in tests --------------------

private class RecordingMigration(
    override val from: LevelDBSchemaVersion,
    override val to: LevelDBSchemaVersion,
    private val label: String,
    private val executed: MutableList<String>,
) : LevelDBMigration {
    override suspend fun migrate(db: LevelDB) {
        executed += label
        db.putString(label, label)
    }
}

private class SlowRecordingMigration(
    override val from: LevelDBSchemaVersion,
    override val to: LevelDBSchemaVersion,
    private val label: String,
    private val executed: MutableList<String>,
    private val delayMs: Long,
) : LevelDBMigration {
    override suspend fun migrate(db: LevelDB) {
        delay(delayMs)
        executed += label
        db.putString(label, label)
    }
}

private class FailingMigration(
    override val from: LevelDBSchemaVersion,
    override val to: LevelDBSchemaVersion,
    private val label: String,
    private val executed: MutableList<String>,
    private val delayMs: Long,
    private val block: () -> Unit = {},
) : LevelDBMigration {
    override suspend fun migrate(db: LevelDB) {
        delay(delayMs)
        executed += label
        db.putString("preFailMarker", "1")
        block()
        throw RuntimeException("boom from $label")
    }
}

// -------------------- fs cleanup --------------------

private fun deleteTree(path: Path) {
    val fs = FileSystem.SYSTEM
    if (!fs.exists(path)) return

    val metadata = fs.metadata(path)
    if (metadata.isDirectory) {
        fs.list(path).forEach { deleteTree(it) }
    }
    fs.delete(path)
}
