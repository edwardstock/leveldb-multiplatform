/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.migration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins the hand-written [LevelDBSchema] equals/hashCode.
 *
 * The override exists only because [LevelDBSchema.versionKey] / [LevelDBSchema.inProgressKey] are
 * `ByteArray` (default data-class equality would compare them by reference). The risk with a
 * hand-written equals is forgetting a field — so this test asserts that EVERY declared property
 * changes equality, and that the two ByteArray keys are compared by content, not identity.
 */
class LevelDBSchemaTest {

    private fun schema() = LevelDBSchema(
        targetVersion = 2,
        revision = 1,
        migrations = listOf(SoftMigration(0, 1), SoftMigration(1, 2)),
        migrateAutomatically = true,
        safety = LevelDBMigrationSafetyPolicy.NONE,
        backupDirName = ".bak",
        stagingDirName = ".stg",
    )

    @Test
    fun `schemas with identical fields are equal and share a hashCode`() {
        val a = schema()
        // Separately constructed: the default versionKey/inProgressKey are distinct ByteArray
        // instances with identical content, so equality must use contentEquals, not reference.
        val b = schema()

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        // reference-equality fast path
        assertEquals(a, a)
    }

    @Test
    fun `every declared field participates in equality`() {
        val base = schema()

        assertNotEquals(base, base.copy(targetVersion = 99))
        assertNotEquals(base, base.copy(revision = 99))
        assertNotEquals(base, base.copy(migrations = emptyList()))
        assertNotEquals(base, base.copy(migrateAutomatically = false))
        assertNotEquals(base, base.copy(safety = LevelDBMigrationSafetyPolicy.BACKUP_DIR))
        assertNotEquals(base, base.copy(versionKey = "other-version-key".encodeToByteArray()))
        assertNotEquals(base, base.copy(inProgressKey = "other-progress-key".encodeToByteArray()))
        assertNotEquals(base, base.copy(backupDirName = "other-backup"))
        assertNotEquals(base, base.copy(stagingDirName = "other-staging"))
    }

    @Test
    fun `differing fields generally yield differing hashCodes`() {
        val base = schema()

        // Not strictly required by the contract, but each field feeds hashCode, so flipping one
        // must perturb it here (guards against a field being dropped from hashCode only).
        assertNotEquals(base.hashCode(), base.copy(revision = 99).hashCode())
        assertNotEquals(base.hashCode(), base.copy(safety = LevelDBMigrationSafetyPolicy.STAGING_DB).hashCode())
        assertNotEquals(base.hashCode(), base.copy(backupDirName = "x").hashCode())
        assertNotEquals(base.hashCode(), base.copy(stagingDirName = "x").hashCode())
        assertNotEquals(base.hashCode(), base.copy(migrateAutomatically = false).hashCode())
    }

    @Test
    fun `not equal to null or to a foreign type`() {
        val base = schema()

        assertFalse(base.equals(null))
        assertFalse(base.equals("not a schema"))
        assertTrue(base == base)
    }
}
