package com.edwardstock.leveldb.migration

import com.edwardstock.leveldb.api.LevelDB

/**
 * A *no-op* migration that only bumps the stored schema version.
 *
 * ## Why does this exist?
 * LevelDB is a key-value store, not a relational DB. Many “schema changes” are actually
 * **additive** and do not require rewriting existing data:
 * - you add new keys
 * - you add derived indices that can be rebuilt lazily
 * - you extend protobuf models by adding new optional fields
 *
 * In such cases you may still want to bump the database version to:
 * - make the on-disk contract explicit (debugging, telemetry, support)
 * - gate feature usage by version (or by presence of keys)
 * - align versions across platforms/clients
 *
 * `SoftMigration` intentionally performs **no data transformation**.
 * It exists purely to make a version jump legal inside [LevelDBSchema]'s migration graph.
 *
 * ## Safety contract
 * Use this only when the new version is **compatible** with older data:
 * - Old clients do not break if new clients write extra keys.
 * - New clients can operate if the new keys are absent (create them lazily or treat as optional).
 * - Value encodings are not changed in a way that older readers would misinterpret.
 *
 * If you need to rename keys, rewrite encodings, rebuild mandatory indices, or delete/transform
 * existing values: write a real [LevelDBMigration].
 *
 * ## Example: “additive” version bump
 * ```kotlin
 * val schema = LevelDBSchema(
 *   targetVersion = 3,
 *   migrations = listOf(
 *     SoftMigration(1, 2),              // v2 adds new optional fields / extra keys
 *     BreakingMigrationV3(from = 2, to = 3) // v3 rewrites existing values
 *   )
 * )
 * ```
 */
data class SoftMigration(
    override val from: Int,
    override val to: Int,
    override val name: String = "Bump version $from -> $to",
) : LevelDBMigration {
    override suspend fun migrate(db: LevelDB) = Unit
}
