package com.edwardstock.leveldb

import com.edwardstock.leveldb.operations.LevelDBOps
import com.edwardstock.leveldb.operations.LevelDBOpsImpl
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/*
 * Copyright (c) 2025 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

/**
 * Coroutine context key for reentry tracking in LevelDBInstance
 */
internal data object ReentryKey : CoroutineContext.Key<ReentryToken>

/**
 * Reentry token stored in coroutine context for nested access tracking
 */
internal class ReentryToken : AbstractCoroutineContextElement(ReentryKey)

/**
 * Shared instance manager that serializes opens and scopes read/write access per path
 */
open class LevelDBInstance(
    private val path: Path,
    private val config: LevelDBConfig = LevelDBConfig(createIfMissing = true),
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    protected val factory: LevelDBFactory = LevelDB.DEFAULT_FACTORY,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : CoroutineScope by scope {

    internal data class Entry(
        val path: String,
        val fsId: Any,
        val state: Mutex = Mutex(),           // guards db/refCount/ownersDepth
        val openOnce: Mutex = Mutex(),        // serialize open
        var db: LevelDB? = null,
        val ownersDepth: MutableMap<ReentryToken, Int> = mutableMapOf(),
        var refCount: Int = 0,
    )

    companion object {
        init {
            levelDbLoadNativeLibrary()
        }

        private val indexLock = reentrantLock()
        private val entries = mutableMapOf<String, Entry>()

        private fun canonicalize(fs: FileSystem, p: Path): String {
            if (!fs.exists(p)) {
                fs.createDirectory(p)
            }
            return fs.canonicalize(p).toString()
        }

        private fun fsIdentity(fs: FileSystem): Any = fs

        private fun requireSameFs(entry: Entry, fs: FileSystem) {
            check(entry.fsId === fsIdentity(fs)) {
                "Mismatched FileSystem for path ${entry.path}. Use the same FileSystem as when entry was created."
            }
        }

        private fun entryFor(path: Path, fs: FileSystem = FileSystem.SYSTEM): Entry =
            indexLock.withLock {
                val key = canonicalize(fs, path)
                entries.getOrPut(key) {
                    Entry(
                        path = key,
                        fsId = fsIdentity(fs),
                    )
                }
            }

        // ---- open/close helpers

        private suspend fun ensureOpen(
            entry: Entry,
            factory: LevelDBFactory,
            config: LevelDBConfig,
        ) {
            entry.state.withLock { if (entry.db != null) return }
            entry.openOnce.withLock {
                entry.state.withLock { if (entry.db != null) return }
                val created = factory(entry.path, config)
                var toClose: LevelDB? = null
                entry.state.withLock {
                    if (entry.db == null) entry.db = created else toClose = created
                }
                toClose?.close()
            }
        }

        private suspend fun closeIfIdle(e: Entry) {
            var toClose: LevelDB? = null
            e.state.withLock {
                if (e.refCount == 0 && e.db != null) {
                    toClose = e.db
                    e.db = null
                }
            }
            toClose?.let { db ->
                // Serialize close with open to avoid overlapping handles on the same path
                e.openOnce.withLock {
                    runCatching { db.close() }
                }
            }
        }

        // ---- Exclusivity API (per path) ----------------------------------------------------

        /**
         * Blocks NEW `use{}` for [path], waits until active `use{}` drain (refCount==0),
         * optionally closes DB (removes LevelDB LOCK files), runs [block], then releases
         */
        suspend fun <T> withPathExclusive(
            path: Path,
            fs: FileSystem = FileSystem.SYSTEM,
            closeIfOpen: Boolean = true,
            block: suspend () -> T,
        ): T {
            val e = entryFor(path, fs)
            requireSameFs(e, fs)

            while (true) {
                e.state.lock()
                if (e.refCount == 0) {
                    try {
                        if (closeIfOpen) {
                            val toClose = e.db
                            e.db = null
                            // Closing under state is fine here: no owners exist
                            toClose?.close()
                        }
                        return block()
                    } finally {
                        e.state.unlock()
                    }
                } else {
                    e.state.unlock()
                    yield()
                }
            }
        }
    }

    private val entry: Entry = entryFor(path, fileSystem)

    /**
     * Opens or reuses the per-path DB, reentrant-safe
     *
     * Use this to safely access the shared DB instance across coroutines/threads.
     * Inside the block you can call any [LevelDBOps] operations (read/write).
     */
    suspend fun <T> use(block: suspend LevelDBOps.() -> T): T {
        val existing = coroutineContext[ReentryKey]
        val token = existing ?: ReentryToken()
        val ctx = if (existing == null) coroutineContext + token else coroutineContext

        return withContext(ctx) {
            var isReentrant = false
            entry.state.withLock {
                val depth = entry.ownersDepth[token] ?: 0
                if (depth > 0) {
                    entry.ownersDepth[token] = depth + 1
                    isReentrant = true
                } else {
                    entry.ownersDepth[token] = 1
                    entry.refCount += 1
                }
            }
            if (!isReentrant) {
                // open db if first time for this token
                ensureOpen(
                    entry = entry,
                    factory = factory,
                    config = config,
                )
            }

            try {
                val db = requireNotNull(entry.db) { "LevelDB not opened for ${entry.path}" }
                val ops: LevelDBOps = LevelDBOpsImpl(db)
                ops.block()
            } finally {
                var becameOutermost = false
                entry.state.withLock {
                    val newDepth = (entry.ownersDepth[token] ?: 1) - 1
                    if (newDepth <= 0) {
                        entry.ownersDepth.remove(token)
                        entry.refCount -= 1
                        becameOutermost = entry.refCount == 0
                    } else {
                        entry.ownersDepth[token] = newDepth
                    }
                }
                if (becameOutermost) {
                    closeIfIdle(entry)
                }
            }
        }
    }

    /**
     * Closes the underlying DB if idle and waits for completion
     */
    suspend fun closeAndAwait() {
        withContext(Dispatchers.IO + NonCancellable) {
            closeIfIdle(entry)
        }
    }

    fun close() {
        // best-effort async close; callers can close the DB explicitly when they need determinism
        launch(Dispatchers.IO + NonCancellable) { closeIfIdle(entry) }
    }
}
