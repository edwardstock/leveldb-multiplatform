package com.edwardstock.leveldb.migration

enum class LevelDBMigrationSafetyPolicy {
    /**
     * fastest, no backup
     */
    NONE,

    /**
     * copy db dir before migration
     */
    BACKUP_DIR,

    /**
     * build new db, then swap
     */
    STAGING_DB,
}
