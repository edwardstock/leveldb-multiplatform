package com.edwardstock.leveldb.migration

val LEVELDB_MAGIC_BYTES: ByteArray = byteArrayOf(0xED.toByte(), 0xDB.toByte(), 0xDE.toByte(), 0xAD.toByte())

/**
 * Declares a versioned “schema contract” for a LevelDB database and how to reach [targetVersion].
 *
 * ## What “schema” means here
 * LevelDB itself has no schema. This class defines a *project-level contract*:
 * - a version number stored inside the DB ([versionKey])
 * - a set of migrations that can move the DB from one version to another
 *
 * The version is a convention that helps clients coordinate changes safely.
 *
 * ## How migration works (high-level)
 * When a DB is opened with a [LevelDBSchema] and automatic migration is enabled:
 * 1) Read stored version from [versionKey] (default is 0 if absent).
 * 2) If storedVersion != [targetVersion], resolve a migration path using [migrations].
 * 3) Execute each [LevelDBMigration] step in order.
 * 4) After each successful step, persist the new version in [versionKey].
 * 5) Use [inProgressKey] as a marker to signal that migration is running.
 *
 * If there is no path from storedVersion to [targetVersion], the safe default is to fail.
 * (Silently “bumping” the version without declared steps is a great way to ship corruption
 * to production and then blame the user’s phone.)
 *
 * ## Soft migrations vs real migrations
 * - Use [SoftMigration] when you only need to record a new version and the data remains compatible.
 * - Use real migrations when you must transform existing keys/values.
 *
 * ## Example: Schema with a soft step and a breaking step
 * ```kotlin
 * val schema = LevelDBSchema(
 *   targetVersion = 3,
 *   migrations = listOf(
 *     SoftMigration(1, 2),
 *     object : LevelDBMigration {
 *       override val from = 2
 *       override val to = 3
 *       override val name = "Rewrite user values to v3"
 *
 *       override suspend fun migrate(db: LevelDB) {
 *         // Example pattern: scan keys with a prefix and rewrite values.
 *         val it = db.iterator(fillCache = false)
 *         try {
 *           val prefix = "user:".encodeToByteArray()
 *           it.seek(prefix)
 *           while (it.isValid) {
 *             val k = it.key()
 *             if (!k.startsWith(prefix)) break
 *
 *             val oldValue = it.value()
 *             val newValue = transformUserV2toV3(oldValue)
 *             db.putBytes(k, newValue)
 *
 *             it.next()
 *           }
 *         } finally {
 *           it.close()
 *         }
 *       }
 *     }
 *   )
 * )
 * ```
 *
 * ## Practical advice
 * - Make migrations idempotent (safe to re-run).
 * - Prefer existence checks over trusting “version implies key presence”.
 * - Consider using [LevelDBMigrationSafetyPolicy] if you care about recoverability.
 */
data class LevelDBSchema(
    val targetVersion: Int,
    val revision: Int = 0,
    val migrations: List<LevelDBMigration>,
    val migrateAutomatically: Boolean = true,
    val safety: LevelDBMigrationSafetyPolicy = LevelDBMigrationSafetyPolicy.NONE,
    val versionKey: ByteArray = LEVELDB_MAGIC_BYTES + "_schema_version".encodeToByteArray(),
    val inProgressKey: ByteArray = LEVELDB_MAGIC_BYTES + "_migration_in_progress".encodeToByteArray(),
    val backupDirName: String = ".leveldb_migration_backup",
    val stagingDirName: String = ".leveldb_migration_staging",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as LevelDBSchema

        if (targetVersion != other.targetVersion) return false
        if (migrations != other.migrations) return false
        if (!versionKey.contentEquals(other.versionKey)) return false
        if (!inProgressKey.contentEquals(other.inProgressKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = targetVersion
        result = 31 * result + migrations.hashCode()
        result = 31 * result + versionKey.contentHashCode()
        result = 31 * result + inProgressKey.contentHashCode()
        return result
    }
}

internal fun LevelDBSchema.shouldMigrate(stateSchemaOkForVersion: Int?): Boolean {
    return migrateAutomatically && stateSchemaOkForVersion != targetVersion
}
