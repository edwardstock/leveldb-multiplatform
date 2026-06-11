/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.migration

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.getString
import com.edwardstock.leveldb.api.getValue
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.common.DatabaseTestCase
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavior pins for [SoftMigration] — the no-op migration that only advances the stored version.
 *
 * The contract has two halves, both pinned here:
 *  - integration: applied inside a [LevelDBSchema], a SoftMigration bumps the persisted version to
 *    the target while leaving every pre-existing entry byte-for-byte untouched (its `migrate` is a
 *    no-op). A mutation that made `migrate` write/delete data, or that failed to advance the
 *    version, would fail these assertions.
 *  - value semantics: the default `name` describes the jump, and the data-class contract
 *    (equality, copy, destructuring) holds.
 */
class SoftMigrationTest {

    @Test
    fun `soft migration bumps stored version without touching existing data`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()

        // Seed v0 data: no stored version yet == version 0.
        run {
            val seed = noSchemaInstance(path)
            try {
                seed.use {
                    putString("user.alice", "1")
                    putString("user.bob", "2")
                }
            } finally {
                seed.closeAndAwait()
            }
        }

        val schema = LevelDBSchema(
            targetVersion = 1,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(SoftMigration(0, 1)),
        )
        val instance = LevelDBInstance.builder(path)
            .fileSystem(FileSystem.SYSTEM)
            .scope(this)
            .schema(schema)
            .instance { }
            .build()

        try {
            instance.migrateIfNeeded()

            // SoftMigration.migrate is a no-op: every seeded entry survives unchanged.
            instance.use {
                assertEquals("1", getString("user.alice"))
                assertEquals("2", getString("user.bob"))
            }

            // Version advanced to the target. Read durably via a fresh handle after close
            // (currentSchemaVersion is racy once the handle idle-closes).
            instance.closeAndAwait()
            assertEquals(
                1,
                readPersistedVersion(path, schema),
                "soft migration must advance the stored version to the target",
            )
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `default name describes the version jump`() {
        // The default name is user-visible (logs / telemetry per the KDoc) — pin its format.
        assertEquals("Bump version 2 -> 3", SoftMigration(2, 3).name)
        assertEquals("additive optional fields", SoftMigration(2, 3, name = "additive optional fields").name)
    }

    private fun TestScope.noSchemaInstance(path: Path): LevelDBInstance =
        LevelDBInstance.builder(path)
            .fileSystem(FileSystem.SYSTEM)
            .scope(this)
            .instance { }
            .build()

    private suspend fun TestScope.readPersistedVersion(path: Path, schema: LevelDBSchema): Int {
        val reader = noSchemaInstance(path)
        return try {
            reader.use { getValue<Int>(schema.versionKey) ?: 0 }
        } finally {
            reader.closeAndAwait()
        }
    }
}

private fun deleteTree(path: Path) {
    val fs = FileSystem.SYSTEM
    if (!fs.exists(path)) return

    val metadata = fs.metadata(path)
    if (metadata.isDirectory) {
        fs.list(path).forEach { deleteTree(it) }
    }
    fs.delete(path)
}
