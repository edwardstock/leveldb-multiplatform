# LevelDB Multiplatform

[![CI](https://github.com/edwardstock/leveldb-multiplatform/actions/workflows/ci.yml/badge.svg)](https://github.com/edwardstock/leveldb-multiplatform/actions/workflows/ci.yml)
[![Publish](https://github.com/edwardstock/leveldb-multiplatform/actions/workflows/publish.yml/badge.svg)](https://github.com/edwardstock/leveldb-multiplatform/actions/workflows/publish.yml)
[![Latest release](https://img.shields.io/github/v/release/edwardstock/leveldb-multiplatform)](https://github.com/edwardstock/leveldb-multiplatform/releases/latest)

Kotlin Multiplatform bindings for LevelDB:

- **JVM/Android** via JNI
- **Kotlin/Native** for desktop and iOS

Forked from: https://github.com/hf/leveldb-android

---

## Features

- Unified API across JVM, Android and Kotlin/Native
- Snapshots and iterators
- Typed adapters for common primitives (plus JVM `BigInteger`/`BigDecimal`)
- Coroutine-friendly shared access via `LevelDBInstance`:
    - one handle per filesystem path
    - reentrant `use { }`
    - configurable idle-close strategy
- Schema migrations via `LevelDBSchema` / `LevelDBMigration`
    - linear migration plan (`N -> N+1`)
    - safety policies: in-place / backup / staging+swap
    - in-process “failed migration” guard (to avoid infinite retries)
- Exclusive filesystem access via `useExclusively` for backup/restore workflows

---

## Supported targets

- Android
- JVM
- macOS (x86_64, arm64)
- Linux (x86_64, arm64)
- Windows (x86_64)
- iOS (arm64, simulator arm64)

---

## Installation

Kotlin DSL:

```kotlin
dependencies {
    implementation("com.edwardstock.leveldb:leveldb:<version>")
}
```

Use the latest version from the release badge above.

---

## Quick start

### Recommended: shared `LevelDBInstance`

Build an instance once per path, reuse it via `use { }`.

```kotlin
import okio.Path.Companion.toPath
import kotlin.time.Duration.Companion.seconds

val instance = LevelDBInstance.builder("/path/to/db".toPath()) {
    schema(mySchema) // optional (migrations)
    closeStrategy(LevelDBInstanceConfig.CloseStrategy.IdleDelayed(10.seconds))

    driver {
        createIfMissing(true)
        paranoidChecks(true)
        cacheSize(256 * 1024 * 1024)
        blockSize(32 * 1024)
        writeBufferSize(64 * 1024 * 1024)
    }

    adapters {
        addAdapter(MyCustomAdapter())
    }
}.build()

instance.use {
    put("hello", "shared instance")
}

// Call only when you want deterministic teardown (tests, shutdown hooks)
instance.closeAndAwait()
```

### Manual: raw `LevelDB` open/close

```kotlin
val db = LevelDB.open("/path/to/db") {
    createIfMissing = true
}
db.put("hello", "world")
val value = db.getString("hello")
db.close()
```

Manual mode is fine when lifecycle is short and deterministic.  
If you forget `close()`, the native handle can stay open and you may hit `LOCK` errors or resource leaks.

---

## Exclusive access: `useExclusively`

`useExclusively` blocks new `use {}` calls for the same path, waits for active users to drain, and gives you a safe scope
to mutate/copy/swap the DB directory.

```kotlin
instance.use {
    put("pre-sync", "work")
}

instance.useExclusively { scope ->
    // Safe to touch filesystem (copy/swap/restore)

    // If you need DB access inside exclusivity, use open { }:
    open {
        // do DB operations
    }

    // If you need concurrency inside exclusivity, use the provided scope:
    scope.launch {
        open {
            // still exclusive-safe
        }
    }

    // DO NOT call instance.use { } here (can deadlock if you wait on it)
}
```

Constraints:

- `useExclusively { }` must not be called from inside `use { }` for the same path.
- Nested `useExclusively { }` on the same instance/path is forbidden.
- `open { }` must not be nested inside another `open { }` within the same exclusive block.

---

## Migrations

Migrations are defined by `LevelDBSchema`:

- `targetVersion`: desired schema version
- `migrations`: **linear** list of steps `N -> N+1` (exactly one per hop)

If a step is missing or non-linear, migration fails with `LevelDBMigrationException`.

### How it runs

1) Reads stored version from `schema.versionKey` (defaults to `0` if absent).
2) If `storedVersion == targetVersion`, nothing runs.
3) Otherwise resolves a linear plan and executes steps in order.

Migration runs under path exclusivity:

- New `use {}` are blocked until migration completes.
- Active leases must finish before migration begins.
- After success, DB is reopened and normal access resumes.

### Auto vs manual

- **Auto (default):** first `use {}` triggers migration if needed.
- **Manual:** disable auto-migrate in your schema config (if applicable in your API) and call `instance.migrateIfNeeded()` yourself.

### Crash safety & `inProgressKey`

Migration uses `schema.inProgressKey` to track progress:

- Before each step: write `inProgress = step.to`
- After successful step: set `versionKey = step.to`
- On success: delete `inProgressKey`

After a crash mid-step, next run expects:

- `storedVersion = N` and `inProgress = N+1` → retry current step

Any other `inProgress` value (not `storedVersion + 1` and not `targetVersion`) is treated as invalid and fails.

### Failed-migration guard (in-process)

If a migration step throws, the instance records a failure state **in memory** and stops retrying in the same process:

- subsequent `use {}` attempts throw `LevelDBCorruptedMigrationException`
- this prevents infinite retry loops in one app run
- the guard does **not** survive a full app restart

To retry after your own recovery logic:

```kotlin
instance.migrateIfNeeded(ignorePreviousFailure = true)
```

> `ignorePreviousFailure` is an escape hatch. If you always reset failures, you can create an infinite loop where migration constantly fails.

### Safety policies

Choose based on how much “safety net” you want:

- `NONE` (in-place): fastest, modifies live DB directly
- `BACKUP_DIR`: copies DB to backup dir, migrates in place, restores backup on failure
- `STAGING_DB`: migrates a staging copy, then swaps dirs; rolls back swap on failure

### Example schema

```kotlin
val schema = LevelDBSchema(
    targetVersion = 2,
    safety = LevelDBMigrationSafetyPolicy.BACKUP_DIR,
    migrations = listOf(
        SoftMigration(0, 1),
        object : LevelDBMigration {
            override val from = 1
            override val to = 2
            override val name = "Rewrite user values"
            override suspend fun migrate(db: LevelDB) {
                // transform data here
            }
        }
    )
)

val instance = LevelDBInstance.builder("/path/to/db".toPath()) {
    schema(schema)
}.build()
```

---

## Recovery

If you end up with a broken DB (crash, bad FS state, stale locks), you can try:

```kotlin
LevelDB.repair(path)
```

It calls `leveldb::RepairDB()` under the hood and attempts to rebuild state.
This may cause data loss: use it as a last resort and document it.

---

## Close strategy

Default behavior closes the DB immediately when the last `use {}` finishes.
If you prefer keeping the native handle warm between bursts:

```kotlin
val instance = LevelDBInstance.builder("/path/to/db".toPath()) {
    closeStrategy(LevelDBInstanceConfig.CloseStrategy.IdleDelayed(10.seconds))
}.build()
```

---

## Thread-safety notes

- Database access is thread-safe.
- Iterators and snapshots are **not** thread-safe: do not share them across threads without external synchronization.

---

## Native binaries (JVM)

The JVM artifact bundles native libraries under `natives/<arch>`:

- `natives/linux_64`
- `natives/linux_arm64`
- `natives/osx_64`
- `natives/osx_arm64`
- `natives/windows_64`

For contributors, prebuilt JNI binaries live in `native/prebuilt`.

---

## Publishing

The Publish workflow supports manual runs with a custom version.
By default it runs tests on Linux only; enable full OS tests with `full_tests=true`.

---

## Licenses

This project is distributed under:
- BSD-3 Clause (original LevelDB wrapper code)
- Apache 2.0 (`third_party/stojan`)
- MIT (project root LICENSE)

---

## Attribution

Includes code derived from Stojan Dimitrovski's original LevelDB wrapper.
Original BSD 3-Clause license text is preserved in source headers and `third_party`.
