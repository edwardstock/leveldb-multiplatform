# LevelDB Multiplatform

[![CI](https://github.com/edwardstock/leveldb-multiplatform/actions/workflows/ci.yml/badge.svg)](https://github.com/edwardstock/leveldb-multiplatform/actions/workflows/ci.yml)
[![Publish](https://github.com/edwardstock/leveldb-multiplatform/actions/workflows/publish.yml/badge.svg)](https://github.com/edwardstock/leveldb-multiplatform/actions/workflows/publish.yml)
[![Latest release](https://img.shields.io/github/v/release/edwardstock/leveldb-multiplatform)](https://github.com/edwardstock/leveldb-multiplatform/releases/latest)

Kotlin Multiplatform bindings for [LevelDB](https://github.com/google/leveldb) — the same key-value
store, one API, across JVM, Android, and Kotlin/Native. JVM and Android go through JNI; desktop and
iOS use Kotlin/Native directly.

Forked from [hf/leveldb-android](https://github.com/hf/leveldb-android), rewritten as a multiplatform
library.

## Features

- One API for JVM, Android, and Kotlin/Native
- Snapshots and cursor-style iterators
- Typed values via pluggable adapters (primitives out of the box, plus `BigInteger`/`BigDecimal` on JVM)
- `LevelDBInstance` — a managed handle per path: one open database per directory, reentrant `use {}`,
  configurable idle-close, and `use {}` that stays main-safe by default
- Schema migrations (`LevelDBSchema` / `LevelDBMigration`) with backup/staging safety policies and
  crash-resume
- `useExclusively` for backup, restore, and other whole-directory operations
- In-memory `MockLevelDB` for testing without the native library

## Supported targets

- Android
- JVM
- macOS (x86_64, arm64)
- Linux (x86_64, arm64)
- Windows (x86_64)
- iOS (arm64, simulator arm64)

## Installation

> [!IMPORTANT]
> `2.0.0` is not released yet. It's the in-progress multiplatform rewrite and exists only as a
> snapshot for now — don't pin a release version until one ships. To try it, add the Central
> snapshots repository and depend on `2.0.0-SNAPSHOT`:

```kotlin
repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}

dependencies {
    implementation("com.edwardstock.leveldb:leveldb:2.0.0-SNAPSHOT")
}
```

Snapshots move — each publish overwrites the last, so re-resolve to pick up changes. The native
libraries load themselves on first use, so there is nothing else to wire up.

## Quick start

`LevelDBInstance` is the recommended entry point. Build it once per path and run operations inside
`use {}`:

```kotlin
import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.getString
import com.edwardstock.leveldb.api.putString
import kotlinx.coroutines.runBlocking

val db = LevelDBInstance.builder("/path/to/db").build()

runBlocking {
    db.use {
        putString("hello", "world")
        println(getString("hello"))   // world
    }
}
```

`use {}` is a `suspend` function, so it lives inside a coroutine. By default it moves the blocking
database work off your thread, so calling it from `Dispatchers.Main` on Android is safe.

On Android, resolve the path under the app's files directory with `AndroidLevelDBInstance`:

```kotlin
val db = AndroidLevelDBInstance.builder(context, dbName = "app.ldb").build()
```

Need a quick, synchronous handle without coroutines? Open the raw database directly:

```kotlin
val db = LevelDB.open("/path/to/db")
db.putString("hello", "world")
println(db.getString("hello"))
db.close()
```

This call is synchronous and runs on the calling thread — close it yourself when you're done.

## Which API should I use?

There are two entry points, and they don't overlap:

- **`LevelDBInstance`** — the managed layer. It guarantees one open handle per path, supports
  reentrant `use {}`, closes the handle when idle, runs migrations, and keeps `use {}` main-safe.
  Reach for this on Android and in any long-running or concurrent process.
- **`LevelDB.open()`** — raw access to a single LevelDB handle. Synchronous, no coroutines, no
  lifecycle management. Good for scripts, tests, and short-lived deterministic work.

Pick one per database directory. LevelDB allows a single writer per directory, so opening the same
path through both at once will fail with a lock error. See
[docs/concepts.md](docs/concepts.md) for the full picture.

## Documentation

- [Getting started](docs/getting-started.md) — a step-by-step first database, from open to iterate.
- [Concepts](docs/concepts.md) — the two APIs, lifecycle, the threading model, one-owner-per-path.
- [How-to guides](docs/how-to.md) — adapters, scans, batches, snapshots, exclusive access, recovery.
- [Migrations](docs/migrations.md) — schema versions, migration steps, safety policies, crash-resume.

## Native binaries (JVM)

The JVM artifact bundles the native libraries under `natives/<arch>`:

- `natives/linux_64`, `natives/linux_arm64`
- `natives/osx_64`, `natives/osx_arm64`
- `natives/windows_64`

They load on first use. To load eagerly (for example in `Application.onCreate`), call
`LevelDB.loadNative()`. Contributors building from source will find prebuilt JNI binaries in
`native/prebuilt`.

## Publishing

The Publish workflow supports manual runs with a custom version. By default it runs tests on Linux
only; pass `full_tests=true` to run the full matrix.

## Licenses

This project ships under three licenses:

- BSD-3-Clause for the original LevelDB wrapper code
- Apache 2.0 for `third_party/stojan`
- MIT for the project itself (see `LICENSE`)

## Attribution

Includes code derived from Stojan Dimitrovski's original LevelDB wrapper. The original BSD-3-Clause
license text is preserved in the source headers and under `third_party`.
