package com.edwardstock.leveldb.migration

import com.edwardstock.leveldb.api.LevelDB

typealias LevelDBSchemaVersion = Int


/**
 * A single migration step that transforms DB contents from [from] to [to].
 *
 * ## Contract
 * A migration must bring the DB into a state compatible with version [to].
 * The library will:
 * - execute migration steps in the resolved order
 * - persist the current version after each successful step
 *
 * Throwing an exception signals migration failure.
 *
 * ## Strong recommendations
 * ### 1) Prefer idempotent transformations
 * Migration may be interrupted. If it runs again, it should not corrupt data.
 *
 * Good patterns:
 * - rebuild derived keys from source-of-truth keys
 * - write new keys first, then delete old keys
 * - make writes conditional (“if already migrated, skip”)
 *
 * ### 2) Keep it cheap and predictable
 * Use iterator scanning with `seek(prefix)` + early break, not full DB scans.
 * (LevelDB keys are ordered; prefix scans are efficient.)
 *
 * ### 3) Use batches for bulk changes
 * When rewriting many keys, use [LevelDB.withBatch] or [LevelDB.write] with [WriteBatch]
 * to reduce write amplification and improve speed
 *
 * ## Example: prefix rewrite + batching
 * ```kotlin
 * class RenamePrefixMigration : LevelDBMigration {
 *   override val from = 2
 *   override val to = 3
 *   override val name = "Rename user:<id> -> users/<id>"
 *
 *   override suspend fun migrate(db: LevelDB) {
 *     val prefix = "user:".encodeToByteArray()
 *     val newPrefix = "users/".encodeToByteArray()
 *
 *     val it = db.iterator(fillCache = false)
 *     try {
 *       it.seek(prefix)
 *       db.withBatch(sync = true) {
 *         while (it.isValid) {
 *           val k = it.key()
 *           if (!k.startsWith(prefix)) break
 *
 *           val idPart = k.copyOfRange(prefix.size, k.size)
 *           val newKey = newPrefix + idPart
 *
 *           put(newKey, it.value())
 *           del(k)
 *
 *           it.next()
 *         }
 *       }
 *     } finally {
 *       it.close()
 *     }
 *   }
 * }
 * ```
 *
 * ## Example: build a derived index
 * ```kotlin
 * class BuildEmailIndex : LevelDBMigration {
 *   override val from = 1
 *   override val to = 2
 *   override val name = "Build user_email_index"
 *
 *   override suspend fun migrate(db: LevelDB) {
 *     val userPrefix = "user:".encodeToByteArray()
 *     val indexPrefix = "user_email_index:".encodeToByteArray()
 *
 *     val it = db.iterator(fillCache = false)
 *     try {
 *       it.seek(userPrefix)
 *       db.withBatch(sync = true) {
 *         while (it.isValid) {
 *           val k = it.key()
 *           if (!k.startsWith(userPrefix)) break
 *
 *           val userBytes = it.value()
 *           val email = extractEmail(userBytes) ?: run {
 *             it.next()
 *             return@run
 *           }
 *
 *           val indexKey = indexPrefix + email.encodeToByteArray()
 *           put(indexKey, k) // value points to original user key (or user id)
 *
 *           it.next()
 *         }
 *       }
 *     } finally {
 *       it.close()
 *     }
 *   }
 * }
 * ```
 */
interface LevelDBMigration {
    /** Source schema version this migration expects. */
    val from: LevelDBSchemaVersion

    /** Target schema version after this migration completes successfully. */
    val to: LevelDBSchemaVersion

    /** Human-readable name for logs and diagnostics. */
    val name: String
        get() = this::class.simpleName ?: "UnknownMigration"

    /**
     * Runs the migration
     *
     * Throwing an exception indicates failure. The library will treat the migration as unsuccessful.
     * Keep in mind that failures may occur mid-flight (process death, cancellation, IO issues),
     * so write migrations defensively and prefer idempotent approaches
     */
    suspend fun migrate(db: LevelDB)
}
