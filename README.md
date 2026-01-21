# LevelDB Multiplatform

[![CI](https://github.com/edwardstock/leveldb-multiplatform/actions/workflows/ci.yml/badge.svg)](https://github.com/edwardstock/leveldb-multiplatform/actions/workflows/ci.yml)
[![Publish](https://github.com/edwardstock/leveldb-multiplatform/actions/workflows/publish.yml/badge.svg)](https://github.com/edwardstock/leveldb-multiplatform/actions/workflows/publish.yml)
[![Latest release](https://img.shields.io/github/v/release/edwardstock/leveldb-multiplatform)](https://github.com/edwardstock/leveldb-multiplatform/releases/latest)

Kotlin Multiplatform bindings for LevelDB with JNI (JVM/Android) and Kotlin/Native for desktop and iOS.
Forked from https://github.com/hf/leveldb-android.

## Features

- Common API across JVM, Android, and Kotlin/Native targets
- Snapshot and iterator support
- Typed adapters for common primitives (and JVM BigInteger/BigDecimal)
- Coroutine-friendly access via `LevelDBInstance` (reentrant, shared per path)

## Supported targets

- Android
- JVM
- macOS (x86_64, arm64)
- Linux (x86_64, arm64)
- Windows (x86_64)
- iOS (arm64, simulator arm64)

## Publishing

The Publish workflow supports manual runs with a custom version
By default it runs tests on Linux only, you can enable full OS tests with `full_tests=true`

## Installation

Kotlin DSL:

```kotlin
dependencies {
    implementation("com.edwardstock.leveldb:leveldb:2.0.0")
}
```

## Usage

Basic open/put/get:

```kotlin
val db = LevelDB.open("/path/to/db") {
    createIfMissing = true
}

db.put("hello", "world")
val value = db.getString("hello")

db.close()
```

Android (multiplatform):

```kotlin
// shared code
val instance = LevelDBInstance(path = context.filesDir.resolve("leveldb").absolutePath.toPath())
instance.write { put("hello", "android") }
val value = instance.read { getString("hello") }
instance.closeAndAwait()
```

Android (direct LevelDB API):

```kotlin
val db = LevelDB.open(context.filesDir.resolve("leveldb").absolutePath) {
    createIfMissing = true
}
db.put("hello", "android")
val value = db.getString("hello")
db.close()
```

Snapshots:

```kotlin
val db = LevelDB.open("/path/to/db") { createIfMissing = true }
val snap = db.obtainSnapshot()

db.put("k1", "v1")
val atSnapshot = db.getString("k1", snap) // null if written after snapshot

snap.close()
db.close()
```

Iterators:

```kotlin
val db = LevelDB.open("/path/to/db") { createIfMissing = true }
db.iterator().use { it ->
    it.seekToFirst()
    while (it.isValid) {
        println(it.keyString() + " -> " + it.valueString())
        it.next()
    }
}
db.close()
```

Coroutine-friendly access with a shared instance:

```kotlin
import okio.Path.Companion.toPath

val instance = LevelDBInstance(path = "/path/to/db".toPath())

instance.write { put("a", "1") }
val value = instance.read { getString("a") }

instance.closeAndAwait()
```

## LevelDBInstance: `use` and `withPathExclusive`

`LevelDBInstance.use { ... }` provides a safe, reentrant way to share a single database handle across threads
and coroutines. It guarantees the DB is opened once per path and closed when idle. Use the convenience wrappers
`read { ... }` and `write { ... }` when you don't need the full access object.

In this context, "shared instance" means: all coroutines/threads that use the same filesystem path will reuse
the same underlying LevelDB handle. This avoids multiple native opens on the same DB (which can fail with a LOCK
error) and removes the need for you to manage locks manually. The instance handles reentrancy and lifecycle
so callers can treat the DB like a safe, reusable resource (similar to the way Room hides its internals).

`LevelDBInstance.withPathExclusive(path) { ... }` blocks new `use` calls and waits for active users to drain.
Use it when you need exclusive filesystem access to the DB directory, for example:

- create a full consistent copy of the DB for backup or sync
- replace the entire DB directory with a downloaded snapshot

This is ideal for background sync (e.g., an app that periodically swaps in a remote state). When the block
starts, the DB is fully quiesced; when it ends, normal access resumes. **Do not call `db.use { ... }` inside the
exclusive block** or you will deadlock. Treat it as “hands off the DB” time while you copy or replace the folder.

## Thread-safety

- Database access is thread-safe.
- Iterators and snapshots are not thread-safe and must not be shared across threads without external synchronization.

## Native binaries (JVM)

The JVM artifact bundles native libraries under `natives/<arch>` for SciJava native-lib-loader, using:

- `natives/linux_64`
- `natives/linux_arm64`
- `natives/osx_64`
- `natives/osx_arm64`
- `natives/windows_64`

For contributors, prebuilt JNI binaries live in `native/prebuilt`.

## Licenses

This project is distributed under:

- BSD-3 Clause (original LevelDB wrapper code)
- Apache 2.0 (third_party/stojan)
- MIT (project root LICENSE)

## Attribution

This project includes code derived from Stojan Dimitrovski's original LevelDB wrapper.
The original BSD 3-Clause license text is preserved in the source headers and in `third_party`.
