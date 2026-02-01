package com.edwardstock.leveldb.internal

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.LevelDBInstance.Companion.ensureOpen
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.exception.LevelDBConcurrencyException
import com.edwardstock.leveldb.exception.LevelDBCorruptedMigrationException
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.log.LevelDBLogger
import com.edwardstock.leveldb.migration.shouldMigrate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private fun LevelDBLogger.logExclusiveEvent(event: String, path: String, detail: String? = null) {
    val message = buildString {
        append("ExclusivePath.")
        append(event)
        append(": path=")
        append(path)
        detail?.takeIf { it.isNotBlank() }?.let {
            append(" | ")
            append(it)
        }
    }
    logD(message)
}

internal suspend fun <T> useExclusivelyInternal(
    instance: LevelDBInstance,
    block: suspend ExclusivePathOps.(CoroutineScope) -> T,
): T {
    assertNotInUseContext()

    val logger = instance.config.requireLogger
    val state = instance.state
    val pathKey = state.path

    logger.logExclusiveEvent("useExclusively.enter", pathKey)
    assertExclusivePathNotReentered(pathKey)

    val ctxWithToken = currentCoroutineContext().let { ctx ->
        val existing = ctx[ExclusivePathsKey]
        val token = (existing ?: ExclusivePathsToken(emptySet())).plus(pathKey)
        ctx + token
    }

    return withContext(ctxWithToken) {
        logger.logExclusiveEvent("useExclusively.takingMutex", pathKey)
        state.exclusiveMutex.withLock {
            logger.logExclusiveEvent("useExclusively.mutexAcquired", pathKey)

            state.requestExclusive()

            state.self.withLock {
                state.idleCloseJob?.cancel()
                state.idleCloseJob = null
            }

            logger.logExclusiveEvent("useExclusively.acquiringPermits", pathKey)
            state.access.acquireAllPermits()
            logger.logExclusiveEvent("useExclusively.permitsAcquired", pathKey)

            try {
                withContext(NonCancellable) {
                    val toClose = state.self.withLock {
                        state.db.also { state.db = null }
                    }
                    toClose?.close()
                }

                val receiver = ExclusivePathOps(instance)
                receiver.block(this)
            } finally {
                withContext(NonCancellable) {
                    state.access.releaseAllPermits()
                    state.releaseExclusive()
                }
            }
        }
    }
}

private fun LevelDBLogger.logExclusiveOpen(event: String, path: String) =
    logExclusiveEvent("exclusiveOpen.$event", path)

class ExclusivePathOps internal constructor(
    private val instance: LevelDBInstance
) {
    private val instanceConfig: LevelDBInstanceConfig
        get() = instance.config

    suspend fun <T> open(block: suspend LevelDB.() -> T): T {
        val pathKey = instance.state.path
        assertExclusiveOpenNotReentered(pathKey)

        val ctx = currentCoroutineContext()
        val nextToken = (ctx[ExclusiveOpsKey] ?: ExclusiveOpsToken(emptySet())).plus(pathKey)

        return withContext(ctx + nextToken) {
            ensureOpen(instance.state, instanceConfig)

            instance.schema?.let { schema ->
                if (instance.state.schemaFailedState != null) {
                    throw LevelDBCorruptedMigrationException("Schema migration failed; DB is unusable")
                }

                if (schema.migrateAutomatically && schema.shouldMigrate(instance.state.schemaOkForVersion)) {
                    instance.migrator.run()
                }
            }

            val db = instance.state.self.withLock {
                instance.state.db ?: throw LevelDBException("LevelDB not opened for $pathKey")
            }
            instanceConfig.requireLogger.logExclusiveOpen("start", pathKey)
            db.block().also {
                instanceConfig.requireLogger.logExclusiveOpen("end", pathKey)
            }
        }
    }
}

internal suspend fun assertNotInUseContext() {
    if (currentCoroutineContext()[ReentryKey] != null) {
        throw LevelDBConcurrencyException("useExclusively() must not be called from use()")
    }
}

internal suspend fun assertNotInExclusivePath(pathKey: String) {
    val exclusive = currentCoroutineContext()[ExclusivePathsKey]
    if (exclusive?.contains(pathKey) == true) {
        throw LevelDBConcurrencyException("use() is forbidden inside useExclusively()")
    }
}

internal suspend fun assertExclusiveOpenNotReentered(pathKey: String) {
    if (currentCoroutineContext()[ExclusiveOpsKey]?.contains(pathKey) == true) {
        throw LevelDBConcurrencyException("Nested open() is forbidden in ExclusivePathOps")
    }
}

internal suspend fun assertExclusivePathNotReentered(pathKey: String) {
    if (currentCoroutineContext()[ExclusivePathsKey] != null) {
        val existing = currentCoroutineContext()[ExclusivePathsKey]!!
        if (existing.contains(pathKey)) {
            throw LevelDBConcurrencyException("Nested useExclusively() is forbidden for the same instance")
        }
    }
}
