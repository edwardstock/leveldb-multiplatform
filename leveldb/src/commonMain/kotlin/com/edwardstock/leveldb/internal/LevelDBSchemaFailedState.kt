package com.edwardstock.leveldb.internal

import com.edwardstock.leveldb.migration.LevelDBSchema

internal data class LevelDBSchemaFailedState(
    val targetVersion: Int,
    val fromVersion: Int,
    val inProgress: Int?,
) {
    companion object {
        fun createFailure(
            schema: LevelDBSchema,
            fromVersion: Int,
            inProgressVersion: Int?,
        ) = LevelDBSchemaFailedState(
            targetVersion = schema.targetVersion,
            fromVersion = fromVersion,
            inProgress = inProgressVersion,
        )
    }
}
