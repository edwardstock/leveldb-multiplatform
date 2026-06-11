/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.getValue
import com.edwardstock.leveldb.api.putString
import com.edwardstock.leveldb.api.putValue
import com.edwardstock.leveldb.common.DatabaseTestCase
import com.edwardstock.leveldb.currentSchemaVersion
import com.edwardstock.leveldb.exception.LevelDBMigrationException
import com.edwardstock.leveldb.migration.LevelDBMigration
import com.edwardstock.leveldb.migration.LevelDBMigrationSafetyPolicy
import com.edwardstock.leveldb.migration.LevelDBSchema
import com.edwardstock.leveldb.migration.LevelDBSchemaVersion
import com.edwardstock.leveldb.api.LevelDB
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavior pins for [com.edwardstock.leveldb.internal.LevelDBMigratorImpl] plan validation.
 *
 * Exercised through the public migration entry point ([LevelDBInstance.migrateIfNeeded] / `use`):
 * - resolveLinearPlanOrThrow: ambiguous (two migrations from the same `from`), non-linear (N -> N+2)
 * - downgrade rejection: stored > target is unsupported (forward-only linear history)
 * - already-at-target: no-op that removes a stale inProgressKey marker
 */
class LevelDBMigrationPlanTest {

    @Test
    fun `ambiguous migrations for the same from version must throw`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executed = mutableListOf<String>()

        // Two distinct migrations both declare from = 0. resolveLinearPlanOrThrow must reject this
        // (candidates.size != 1) instead of silently picking one.
        val schema = LevelDBSchema(
            targetVersion = 1,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                PlanMigration(0, 1, "first_0_1", executed),
                PlanMigration(0, 1, "second_0_1", executed),
            ),
        )
        val instance = migrationInstance(path, schema)

        try {
            val ex = assertFailsWith<LevelDBMigrationException> {
                instance.use { putString("x", "y") }
            }
            assertTrue(
                ex.message?.contains("Ambiguous") == true,
                "Expected an ambiguity error, got: ${ex.message}",
            )
            // No step may have run: the plan never resolved.
            assertTrue(executed.isEmpty(), "No migration step should run on an ambiguous plan, ran: $executed")
            // Version must not advance to the target.
            assertTrue(instance.currentSchemaVersion < 1)
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `non-linear migration that skips a version must throw`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()
        val executed = mutableListOf<String>()

        // A single migration 0 -> 2 (skips v1). resolveLinearPlanOrThrow requires from -> from+1,
        // so it must reject m.to != cur + 1 even though the migration "covers" the whole range.
        val schema = LevelDBSchema(
            targetVersion = 2,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                PlanMigration(0, 2, "jump_0_2", executed),
            ),
        )
        val instance = migrationInstance(path, schema)

        try {
            val ex = assertFailsWith<LevelDBMigrationException> {
                instance.use { putString("x", "y") }
            }
            assertTrue(
                ex.message?.contains("Non-linear") == true,
                "Expected a non-linear error, got: ${ex.message}",
            )
            assertTrue(executed.isEmpty(), "No migration step should run on a non-linear plan, ran: $executed")
            assertTrue(instance.currentSchemaVersion < 2)
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `downgrade is rejected - migrations are strictly forward-only`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()

        // Seed stored version = 2 on disk.
        seedStoredVersion(path, storedVersion = 2)

        val executed = mutableListOf<String>()
        // Re-open targeting v1 < stored v2. Downgrade is unsupported (forward-only linear history),
        // so the migrator must reject it and leave the stored version untouched.
        val schema = LevelDBSchema(
            targetVersion = 1,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                PlanMigration(0, 1, "m0_1", executed),
            ),
        )
        val instance = migrationInstance(path, schema)

        try {
            val ex = assertFailsWith<LevelDBMigrationException> {
                instance.use { putString("x", "y") }
            }
            assertTrue(
                ex.message?.contains("downgrade", ignoreCase = true) == true,
                "Expected a downgrade error, got: ${ex.message}",
            )
            assertTrue(executed.isEmpty(), "No migration step should run on a blocked downgrade, ran: $executed")
            // Stored version must remain untouched at 2 — read it durably via a fresh handle after the
            // instance is closed (currentSchemaVersion is racy: it returns 0 once the handle idle-closes).
            instance.closeAndAwait()
            assertEquals(2, readPersistedVersion(path), "stored version must remain untouched at 2")
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    @Test
    fun `already at target is a no-op that clears a stale inProgress marker`() = runTest {
        val path = DatabaseTestCase.createRandomDbPath()

        val schema = LevelDBSchema(
            targetVersion = 1,
            safety = LevelDBMigrationSafetyPolicy.NONE,
            migrations = listOf(
                PlanMigration(0, 1, "m0_1", mutableListOf()),
            ),
        )

        // Seed: stored version already == target (1) and a leftover garbage inProgress marker.
        run {
            val seedInstance = LevelDBInstance.builder(path)
                .fileSystem(FileSystem.SYSTEM)
                .scope(this)
                .instance { }
                .build()
            try {
                seedInstance.use {
                    putValue(schema.versionKey, 1)
                    // Garbage value that does NOT match any valid retry/target semantics.
                    putValue(schema.inProgressKey, 999)
                }
                assertEquals(1, seedInstance.use { getValue<Int>(schema.versionKey) })
                assertEquals(999, seedInstance.use { getValue<Int>(schema.inProgressKey) })
            } finally {
                seedInstance.closeAndAwait()
            }
        }

        val executed = mutableListOf<String>()
        val instance = migrationInstance(
            path,
            schema.copy(
                migrations = listOf(PlanMigration(0, 1, "m0_1", executed)),
            ),
        )

        try {
            // Triggers migrator; storedVersion == target so migrateInPlace takes the early
            // no-op branch and deletes the stale inProgressKey.
            instance.migrateIfNeeded()

            // No migration step should have executed.
            assertTrue(executed.isEmpty(), "Already-at-target must not run steps, ran: $executed")
            // The stale inProgress marker must be cleaned.
            assertNull(
                instance.use { getValue<Int>(schema.inProgressKey) },
                "Stale inProgress marker must be removed when already at target",
            )
            // Version stays at the target — read durably via a fresh handle after closing (avoids the
            // racy currentSchemaVersion, which returns 0 once the handle idle-closes).
            instance.closeAndAwait()
            assertEquals(1, readPersistedVersion(path), "version stays at the target")
        } finally {
            instance.closeAndAwait()
            deleteTree(path)
        }
    }

    private fun TestScope.migrationInstance(
        path: Path,
        schema: LevelDBSchema,
    ): LevelDBInstance {
        return LevelDBInstance.builder(path)
            .fileSystem(FileSystem.SYSTEM)
            .scope(this)
            .schema(schema)
            .instance { }
            .build()
    }

    private suspend fun TestScope.seedStoredVersion(path: Path, storedVersion: Int) {
        val seedInstance = LevelDBInstance.builder(path)
            .fileSystem(FileSystem.SYSTEM)
            .scope(this)
            .instance { }
            .build()
        try {
            // Use the default schema key layout (same defaults the schema-under-test uses).
            val versionKey = LevelDBSchema(targetVersion = storedVersion, migrations = emptyList()).versionKey
            seedInstance.use { putValue(versionKey, storedVersion) }
            assertEquals(storedVersion, seedInstance.use { getValue<Int>(versionKey) })
        } finally {
            seedInstance.closeAndAwait()
        }
    }

    /**
     * Reads the durable on-disk schema version via a fresh no-schema instance.
     *
     * Why not [com.edwardstock.leveldb.currentSchemaVersion]: it reads `state.db?.getValue(...) ?: 0`, so
     * it returns 0 whenever the instance's handle is idle/Immediate-closed — a racy result that depends on
     * close timing (the field is only valid right after an open). leveldb also holds an exclusive directory
     * lock, so this MUST run after the caller's instance is closed (a second concurrent handle can't open).
     */
    private suspend fun TestScope.readPersistedVersion(path: Path): Int {
        val reader = LevelDBInstance.builder(path)
            .fileSystem(FileSystem.SYSTEM)
            .scope(this)
            .instance { }
            .build()
        return try {
            reader.use { getValue<Int>(LevelDBSchema(targetVersion = 1, migrations = emptyList()).versionKey) ?: 0 }
        } finally {
            reader.closeAndAwait()
        }
    }
}

// -------------------- migrations used in tests --------------------

private class PlanMigration(
    override val from: LevelDBSchemaVersion,
    override val to: LevelDBSchemaVersion,
    override val name: String,
    private val executed: MutableList<String>,
) : LevelDBMigration {
    override suspend fun migrate(db: LevelDB) {
        executed += name
        db.putString(name, name)
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
