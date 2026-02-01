package com.edwardstock.leveldb

import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.getValue
import com.edwardstock.leveldb.api.putValue
import com.edwardstock.leveldb.config.LevelDBDriverConfig
import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.config.LevelDBInstanceConfig.CloseStrategy
import com.edwardstock.leveldb.config.createDB
import com.edwardstock.leveldb.exception.LevelDBCorruptedMigrationException
import com.edwardstock.leveldb.exception.LevelDBCorruptionException
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.exception.LevelDBMigrationException
import com.edwardstock.leveldb.internal.ExclusivePathOps
import com.edwardstock.leveldb.internal.LevelDBEntryState
import com.edwardstock.leveldb.internal.LevelDBMigrator
import com.edwardstock.leveldb.internal.LevelDBMigratorImpl
import com.edwardstock.leveldb.internal.ReentryKey
import com.edwardstock.leveldb.internal.ReentryToken
import com.edwardstock.leveldb.internal.assertNotInExclusivePath
import com.edwardstock.leveldb.internal.canonicalize
import com.edwardstock.leveldb.internal.checkSameFileSystem
import com.edwardstock.leveldb.internal.duration
import com.edwardstock.leveldb.internal.fsIdentity
import com.edwardstock.leveldb.internal.levelDbLoadNativeLibrary
import com.edwardstock.leveldb.internal.shouldCloseImmediately
import com.edwardstock.leveldb.internal.useExclusivelyInternal
import com.edwardstock.leveldb.log.LevelDBLogger
import com.edwardstock.leveldb.migration.LevelDBSchema
import com.edwardstock.leveldb.migration.shouldMigrate
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/*
 * Copyright (c) 2025 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

/**
 * Shared instance manager that serializes opens and scopes read/write access per path
 */
open class LevelDBInstance internal constructor(
    val path: Path,
    val config: LevelDBInstanceConfig = LevelDBInstanceConfig(),
    val fileSystem: FileSystem = FileSystem.SYSTEM,
    val closeStrategy: CloseStrategy = CloseStrategy.Immediate,
    val timeSource: TimeSource = TimeSource.Monotonic,
    val schema: LevelDBSchema? = null,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : CoroutineScope by scope {

    private val logger = config.requireLogger

    companion object {
        init {
            levelDbLoadNativeLibrary()
        }

        private val indexLock = reentrantLock()
        private val entries = mutableMapOf<String, LevelDBEntryState>()

        internal fun stateFor(path: Path, fs: FileSystem = FileSystem.SYSTEM): LevelDBEntryState =
            indexLock.withLock {
                val canonicalPath = canonicalize(fs, path)
                val key = canonicalPath + fsIdentity(fs).hashCode().toString()
                val existing = entries[key]
                if (existing != null) {
                    checkSameFileSystem(existing, fs)
                    return existing
                }

                LevelDBEntryState(
                    path = canonicalPath,
                    fsId = fsIdentity(fs),
                ).also { entries[key] = it }
            }

        internal suspend fun ensureOpen(
            entry: LevelDBEntryState,
            config: LevelDBInstanceConfig,
        ) {
            entry.self.withLock { if (entry.db != null) return }
            entry.openOnce.withLock {
                entry.self.withLock { if (entry.db != null) return }
                val created = config.createDB(entry.path)
                var toClose: LevelDB? = null
                entry.self.withLock {
                    if (entry.db == null) entry.db = created else toClose = created
                }
                toClose?.close()
            }
        }

        private suspend fun closeIfIdle(state: LevelDBEntryState) {
            val shouldClose: Boolean = state.self.withLock {
                state.refCount == 0 && state.db != null
            }
            if (!shouldClose) return

            state.openOnce.withLock {
                var toClose: LevelDB? = null
                state.self.withLock {
                    if (state.refCount == 0 && state.db != null) {
                        toClose = state.db
                        state.db = null
                    }
                }
                toClose?.let { db ->
                    runCatching { db.close() }
                }
            }
        }

        private suspend fun cancelScheduledClose(state: LevelDBEntryState) {
            val job = state.self.withLock {
                val j = state.idleCloseJob
                state.idleCloseJob = null
                j
            }
            job?.cancel()
        }

        /**
         * Schedules close after [driver.idleCloseDelay] if DB stays idle.
         * Must be called only when refCount just became 0 (outermost exit).
         */
        private suspend fun scheduleCloseIfIdle(
            instance: LevelDBInstance,
            state: LevelDBEntryState,
        ) {
            val closeStrategy = instance.closeStrategy
            if (closeStrategy.shouldCloseImmediately) {
                instance.logEvent("scheduleCloseIfIdle.immediateClose", detail = "strategy=immediate")
                closeIfIdle(state)
                return
            }

            // skip if already scheduled
            if (!state.canScheduleDbClose()) {
                instance.logEvent("scheduleCloseIfIdle.skipCannotSchedule", detail = "refCount=${state.refCount}")
                return
            }

            val job = instance.launch {
                instance.logEvent("scheduleCloseIfIdle.scheduled", detail = "delay=${closeStrategy.duration}")
                delay(closeStrategy.duration)

                instance.logEvent("scheduleCloseIfIdle.delayFinished")

                if (state.canCloseDb(closeStrategy)) {
                    instance.logEvent("scheduleCloseIfIdle.closing")
                    closeIfIdle(state)
                }
            }
            job.invokeOnCompletion {
                instance.launch {
                    state.self.withLock {
                        instance.logEvent("scheduleCloseIfIdle.jobCompleted")
                        if (state.idleCloseJob === job) state.idleCloseJob = null
                    }
                }
            }

            // double check to prevent race condition, or save the newly launched job
            state.self.withLock {
                if (state.refCount != 0 || state.db == null) {
                    instance.logEvent("scheduleCloseIfIdle.cancelled")
                    job.cancel()
                } else {
                    instance.logEvent("scheduleCloseIfIdle.jobSaved")
                    state.idleCloseJob = job
                }
            }
        }

        /**
         * Runs an **exclusive** critical section for a database [LevelDBInstance] path.
         *
         * What this does:
         * - **Blocks new** [LevelDBInstance.use] calls for the same DB path.
         * - Waits until all active `use {}` blocks complete (refCount/permits drain to zero).
         * - Optionally closes the currently opened DB handle (`closeIfOpen=true`) to release native resources
         *   and remove LevelDB lock files before executing [block].
         * - Executes [block] inside an exclusive scope and then releases the lock.
         *
         * ### Contract
         * - You MUST NOT call `useExclusively*()` from inside `use {}` (re-entrancy is forbidden).
         * - While inside this exclusive scope, you MUST NOT call `use {}` for the same path.
         *   Use [ExclusivePathOps.open] instead.
         *
         * ### Deadlock warning (important, yes really)
         * This function **waits** for all active `use {}` blocks to finish.
         * If you start a coroutine from some **external scope** inside [block] and then call `use {}` there,
         * and you also wait for it (directly or indirectly), you create a classic deadlock:
         *
         * - `useExclusively` holds the exclusive barrier and waits for `use {}` to drain.
         * - Your external coroutine tries to enter `use {}` and waits for exclusivity to end.
         * - You wait for that coroutine inside `useExclusively`.
         *
         * To do concurrent work inside [block], use the provided [CoroutineScope] receiver and call
         * [ExclusivePathOps.open] in child coroutines launched from that scope.
         */
        suspend fun <T> useExclusively(
            instance: LevelDBInstance,
            block: suspend ExclusivePathOps.(CoroutineScope) -> T,
        ): T = useExclusivelyInternal(instance, block)

        /**
         * Starts a builder for the given filesystem [path].
         */
        fun builder(path: Path) = Builder(path)

        /**
         * Variant of [builder] that accepts a string location.
         */
        fun builder(path: String) = Builder(path.toPath())

        /**
         * Shortcut that applies [block] to the builder and returns a configured instance.
         */
        fun builder(path: Path, block: Builder.() -> Unit): LevelDBInstance =
            Builder(path).apply(block).build()

        /**
         * Path string variant of the DSL-style [builder].
         */
        fun builder(path: String, block: Builder.() -> Unit): LevelDBInstance =
            builder(path.toPath(), block)
    }

    /** Builder that configures [LevelDBInstance] lifecycle, adapters, and native driver settings. */
    open class Builder internal constructor(
        protected var path: Path
    ) {
        constructor(path: String) : this(path.toPath())

        private var fileSystem: FileSystem = FileSystem.SYSTEM
        private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private var closeStrategy: CloseStrategy = CloseStrategy.Immediate
        private var timeSource: TimeSource = TimeSource.Monotonic
        private var schema: LevelDBSchema? = null
        private var instanceConfigBuilder: LevelDBInstanceConfig.Builder = LevelDBInstanceConfig.builder()

        /**
         * Swap to a custom [FileSystem] when opening the DB.
         *
         * Useful in tests or when wrapping [LevelDB] with a fake or sandboxed filesystem.
         */
        fun fileSystem(fileSystem: FileSystem) = apply { this.fileSystem = fileSystem }

        /**
         * Execute coroutines using a different [CoroutineScope].
         *
         * Allows you to tie the instance lifetime to a specific dispatcher or job hierarchy.
         */
        fun scope(scope: CoroutineScope) = apply { this.scope = scope }

        /**
         * Provide the migration [schema] applied during open.
         *
         * See [LevelDBSchema] for how automatic migrations work.
         */
        fun schema(schema: LevelDBSchema?) = apply { this.schema = schema }

        /**
         * Select how aggressively the idle close job runs after `use {}` exits.
         *
         * The [CloseStrategy] controls whether the native handle stays open or closes immediately.
         */
        fun closeStrategy(closeStrategy: CloseStrategy) = apply { this.closeStrategy = closeStrategy }

        /**
         * Override the default monotonic [TimeSource].
         *
         * Typically only needed in tests that control virtual time.
         */
        fun timeSource(timeSource: TimeSource) = apply { this.timeSource = timeSource }

        /**
         * Replace the held config with an existing [LevelDBInstanceConfig].
         *
         * Handy when you already built/configured a config elsewhere.
         */
        fun instance(config: LevelDBInstanceConfig) = apply { instanceConfigBuilder = LevelDBInstanceConfig.Builder(config) }

        /**
         * Tune the held config through a receiver block.
         *
         * The block receives [LevelDBInstanceConfig.Builder] so you can call `driver { ... }`, `adapters { ... }` etc.
         */
        fun instance(config: LevelDBInstanceConfig.Builder.() -> Unit) = apply { instanceConfigBuilder.apply(config) }

        /**
         * Shortcut to override the logger stored inside the config.
         *
         * Equivalent to calling `instance { logger(...) }`.
         */
        fun logger(logger: LevelDBLogger) = apply { instanceConfigBuilder.logger(logger) }

        /**
         * Tune the underlying driver config via the builder DSL.
         *
         * Calls into [LevelDBDriverConfig.Builder], see that class for option descriptions.
         */
        fun driver(block: LevelDBDriverConfig.Builder.() -> Unit) = apply {
            instanceConfigBuilder.driver(block)
        }

        /**
         * Mutate adapters through the registry receiver block.
         *
         * Use [AdapterRegistry.addAdapter] inside to register custom adapters like converters or primitives.
         */
        fun adapters(block: AdapterRegistry.() -> Unit) = apply {
            instanceConfigBuilder.adapters(block)
        }

        fun path(path: Path) = apply { this.path = path }
        fun path(path: String) = apply { path(path.toPath()) }

        /** Builds a [LevelDBInstance] using the current builder state. */
        open fun build(): LevelDBInstance {
            return createInstance(
                path = path,
                config = instanceConfigBuilder.build(),
                fileSystem = fileSystem,
                scope = scope,
            )
        }

        protected open fun createInstance(
            path: Path,
            config: LevelDBInstanceConfig,
            fileSystem: FileSystem,
            scope: CoroutineScope,
        ): LevelDBInstance = LevelDBInstance(
            path = path,
            config = config,
            fileSystem = fileSystem,
            closeStrategy = closeStrategy,
            timeSource = timeSource,
            schema = schema,
            scope = scope,
        )
    }

    internal val state: LevelDBEntryState = stateFor(path, fileSystem)
    internal val migrator: LevelDBMigrator = LevelDBMigratorImpl(this)

    private fun logEvent(event: String, token: ReentryToken? = null, detail: String? = null) {
        val message = buildString {
            append("LevelDBInstance.")
            append(event)
            append(": path=${state.path}")
            token?.let { append(", token=$it") }
            detail?.takeIf { it.isNotBlank() }?.let {
                append(" | ")
                append(it)
            }
        }
        logger.logD(message)
    }

    private fun logUseEvent(event: String, token: ReentryToken, detail: String? = null) =
        logEvent("use.$event", token, detail)

    /**
     * Opens (or reuses) a LevelDB handle for this instance path and runs [block] with a [LevelDBOps] facade
     *
     * This is the standard way to perform operations with automatic open/close and concurrency control
     *
     * What you get:
     * - Safe reuse of an already opened DB handle for the same path
     * - Concurrent reads and writes according to the underlying LevelDB guarantees and this library's guards
     * - Automatic close when the last user leaves (depending on configuration/implementation)
     *
     * Relationship with `useExclusively {}`:
     * - `useExclusively {}` is an exclusive barrier for the same path
     * - If some coroutine currently holds exclusivity, `use {}` will wait until exclusivity ends
     * - While you are inside `useExclusively {}`, calling `use {}` for the same path is forbidden (it can deadlock)
     *   Use `ExclusivePathOps.open {}` instead
     *
     * Allowed:
     * - Calling `use {}` concurrently from multiple coroutines for the same path
     * - Nested `use {}` in the same coroutine when reentry is supported by the implementation
     *   Example:
     *   ```kotlin
     *   db.use {
     *       putBytes("k".encodeToByteArray(), "v".encodeToByteArray())
     *       db.use {
     *           // safe
     *           getBytes("k".encodeToByteArray())
     *       }
     *   }
     *   ```
     *
     * Forbidden:
     * - Calling `useExclusively {}` from inside `use {}` for the same instance/path
     *   Rationale: `useExclusively` needs to drain active `use` users, including you
     *
     * - Calling `use {}` from inside `useExclusively {}` using any scope
     *   Rationale: `useExclusively` waits for `use` to drain, and `use` waits for exclusivity to end
     *
     * How to do work inside `useExclusively {}`:
     * - Use the provided exclusive API and call `open {}` there
     *   Example:
     *   ```kotlin
     *   db.useExclusively {
     *       open {
     *           putBytes("k".encodeToByteArray(), "v".encodeToByteArray())
     *       }
     *   }
     *   ```
     *  @see Companion.useExclusively
     */
    suspend fun <T> use(block: suspend LevelDB.() -> T): T {
        val existing = coroutineContext[ReentryKey]
        val token = existing ?: ReentryToken()
        val ctx = if (existing == null) coroutineContext + token else coroutineContext
        var acquiredAccess = false

        logUseEvent("enter", token, "existing=$existing")
        return withContext(ctx) {
            val job = coroutineContext.job
            val pathKey = state.path
            assertNotInExclusivePath(pathKey)
            logUseEvent("postponeClose", token)
            state.markUsing(timeSource)

            var isReentrant = false
            val schemaOkForVersion = state.self.withLock {
                val depth = state.ownersDepth[job] ?: 0
                if (depth > 0) {
                    state.ownersDepth[job] = depth + 1
                    isReentrant = true
                    logUseEvent("reentrant", token, "depth=$depth")
                } else {
                    state.ownersDepth[job] = 1
                    state.refCount += 1
                    logUseEvent("newOwner", token, "depth=1")
                }

                state.schemaOkForVersion
            }
            if (!isReentrant) {
                tryMigrate(schemaOkForVersion, token)

                logUseEvent("waitingForAccess", token, "exclusiveGateActive=${state.exclusiveGate?.isActive == true}")
                state.acquireSharedPermit()
                acquiredAccess = true

                // open db if first time for this token
                logUseEvent("opening", token)
                ensureOpen(
                    entry = state,
                    config = config,
                )
            }

            try {
                val db: LevelDB = state.self.withLock {
                    if (state.db == null) {
                        throw LevelDBException("LevelDB not opened for ${state.path}")
                    } else {
                        state.db!!
                    }
                }
                logUseEvent("obtainedDb", token)
                db.block()
            } finally {
                var becameOutermost = false
                state.self.withLock {
                    logUseEvent("releasing", token)
                    val newDepth = (state.ownersDepth[job] ?: 1) - 1
                    if (newDepth <= 0) {
                        state.ownersDepth.remove(job)
                        state.refCount -= 1
                        becameOutermost = state.refCount == 0
                    } else {
                        state.ownersDepth[job] = newDepth
                    }
                }

                logUseEvent("released", token, "becameOutermost=$becameOutermost")
                if (becameOutermost) {
                    scheduleCloseIfIdle(this@LevelDBInstance, state)
                }

                logUseEvent("releasingAccess", token, "acquired=$acquiredAccess")
                if (acquiredAccess) {
                    state.access.release()
                    logUseEvent("releasedAccess", token)
                }
            }
        }
    }

    @Throws(LevelDBMigrationException::class, LevelDBCorruptionException::class, CancellationException::class)
    internal suspend fun tryMigrate(schemaOkForVersion: Int?, token: ReentryToken) {
        val schemaLocal = schema
        if (schemaLocal != null && schemaLocal.shouldMigrate(schemaOkForVersion)) {
            if (state.schemaFailedState != null) {
                throw LevelDBCorruptedMigrationException("Schema migration failed; DB is unusable")
            }
            logUseEvent("schemaOutOfDate", token, "taking exclusive path access")
            state.withExclusive {
                ensureOpen(state, config)
                migrator.run()
            }
        }
        logUseEvent("schemaOk", token)
    }

    /**
     * @see Companion.useExclusively
     */
    suspend fun <T> useExclusively(
        block: suspend ExclusivePathOps.(CoroutineScope) -> T,
    ): T = useExclusively(this, block)

    /**
     * Ensures database schema is migrated to targetVersion if schema is configured.
     *
     * Runs under path exclusivity, so it blocks new `use {}` for the same path and waits for active users to drain.
     * If migration is impossible (missing step), throws [LevelDBMigrationException].
     * @param ignorePreviousFailure If true, clears the previous failure marker before attempting migration
     * Use only when you intentionally implement your own recovery flow:
     * - catch the migration error, perform remediation, then retry
     *
     * WARNING: blindly resetting the failure marker can cause an infinite retry loop
     * (e.g., on app start) if the migration keeps failing deterministically
     */
    suspend fun migrateIfNeeded(ignorePreviousFailure: Boolean = false) = useExclusively {
        if (ignorePreviousFailure) {
            state.schemaFailedState = null
        }
        ensureOpen(state, config)
        migrator.run()
    }

    /**
     * Closes the underlying DB if idle and waits for completion
     */
    suspend fun closeAndAwait() = withContext(NonCancellable) {
        cancelScheduledClose(state)
        closeIfIdle(state)
    }

    fun close() {
        // best-effort async close; callers can close the DB explicitly when they need determinism
        launch(NonCancellable) {
            cancelScheduledClose(state)
            closeIfIdle(state)
        }
    }

}

internal var LevelDBInstance.currentSchemaVersion: Int
    get() {
        val schemaDescriptor = schema ?: return 0
        return state.db?.getValue(schemaDescriptor.versionKey, Int::class) ?: 0
    }
    internal set(v) {
        val schemaDescriptor = schema ?: return
        state.db?.putValue(schemaDescriptor.versionKey, v)
    }

internal var LevelDBInstance.currentInProgressMigration: Int?
    get() {
        val schemaDescriptor = schema ?: return null
        return state.db?.getValue(schemaDescriptor.inProgressKey, Int::class)
    }
    internal set(v) {
        val schemaDescriptor = schema ?: return
        state.db?.putValue(schemaDescriptor.inProgressKey, v)
    }
