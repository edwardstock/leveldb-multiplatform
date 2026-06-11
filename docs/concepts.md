# Concepts

This page explains how the library is put together — the two entry points, how the managed handle
lives and dies, and the threading rules. Read it once and the rest of the API stops being surprising.

## Two entry points

There are two ways into the database, and they're meant for different jobs.

### `LevelDBInstance` — the managed layer

`LevelDBInstance` is bound to one directory. It doesn't open the database when you build it; it opens
on the first `use {}` and manages the handle from there:

```kotlin
val db = LevelDBInstance.builder("/path/to/db").build()

db.use {
    putString("k", "v")
}
```

What it gives you:

- **One handle per path.** Every `LevelDBInstance` for the same directory shares a single open
  database, tracked in a process-wide registry. Build ten instances for one path and you still get
  one handle.
- **Reentrant `use {}`.** Calling `use {}` from inside another `use {}` on the same coroutine reuses
  the handle instead of deadlocking.
- **Idle-close.** When the last `use {}` finishes, the handle can close — immediately, or after a
  delay (see [Lifecycle](#lifecycle-and-idle-close)).
- **Migrations.** If you attach a schema, the first `use {}` runs any pending migration under an
  exclusive lock. See [Migrations](migrations.md).
- **Main-safe operations.** `use {}` moves blocking work off your thread by default.

This is the right default for Android and for any process that lives longer than a single operation.

### `LevelDB.open()` — raw access

`LevelDB.open()` hands you a single LevelDB handle and gets out of the way:

```kotlin
val db = LevelDB.open("/path/to/db")
db.putString("k", "v")
db.close()
```

It's synchronous, has no coroutines, and does no lifecycle management — you opened it, you close it.
It also takes a `LevelDBInstanceConfig` but ignores the `instanceFactory` field on it (that field
belongs to the managed layer); only the `driver` settings apply here. Use this for scripts, tests,
and short deterministic tasks where you control the lifetime yourself.

If you forget to `close()`, the native handle stays open and you can hit lock errors or leak
resources. That's the trade-off for skipping the managed layer.

## Lifecycle and idle-close

Opening a LevelDB directory is not free, so `LevelDBInstance` doesn't necessarily close the handle the
moment a `use {}` block exits. A close strategy decides what happens:

```kotlin
import com.edwardstock.leveldb.config.LevelDBInstanceConfig.CloseStrategy
import kotlin.time.Duration.Companion.seconds

val db = LevelDBInstance.builder("/path/to/db")
    .closeStrategy(CloseStrategy.IdleDelayed(10.seconds))
    .build()
```

- `CloseStrategy.Immediate` (the default) closes the handle as soon as the last user leaves.
- `CloseStrategy.IdleDelayed(duration)` keeps the handle warm for `duration` after the last user
  leaves. If a new `use {}` arrives in that window, it reuses the open handle and the timer resets.

`IdleDelayed` is worth it when you read and write in bursts — you skip repeated open/close cycles
between them. For a one-shot operation, `Immediate` is fine.

When you need the handle closed right now (a test tearing down, a service shutting down), don't wait
for the timer:

```kotlin
db.closeAndAwait()   // suspends until the handle is closed
```

There's also a fire-and-forget `db.close()`, but it returns before the close actually happens. Prefer
`closeAndAwait()` when you care about ordering.

## The threading model

`use {}` is a `suspend` function for two reasons: its internal locking is coroutine-based, and it
moves the blocking native I/O onto a worker so it doesn't tie up your caller.

### Which dispatcher runs the work

The dispatcher for operations inside `use {}` is resolved in this order:

1. an explicit `dispatcher(...)` set on the builder, otherwise
2. the dispatcher of the instance's `scope`, otherwise
3. `Dispatchers.IO`.

The default scope uses `Dispatchers.IO`, so out of the box `use {}` is **main-safe**: you can call it
from `Dispatchers.Main` and the blocking work still runs on IO.

```kotlin
// On Android — safe, the put runs on IO, not on the main thread:
viewModelScope.launch {
    db.use { putString("k", "v") }
}
```

If you want operations on a specific dispatcher regardless of the caller, set it explicitly:

```kotlin
val db = LevelDBInstance.builder("/path/to/db")
    .dispatcher(myDispatcher)
    .build()
```

A reentrant `use {}` (nested in another on the same coroutine) inherits the outer dispatcher rather
than switching again.

The raw `LevelDB.open()` path makes no such promise — it's synchronous and runs on whatever thread
calls it. If you open raw on the main thread, the I/O is on the main thread. That's by design: the
managed layer owns threading, the raw layer leaves it to you.

### What is and isn't thread-safe

- **Database operations are thread-safe.** Concurrent reads and writes are fine.
- **Iterators and snapshots are not.** Don't share a single iterator or snapshot across threads
  without your own synchronization. Each thread that iterates should get its own iterator.

## One owner per path

LevelDB allows a single writer per directory. It enforces this with a `LOCK` file: the second attempt
to open the same directory fails with a lock error.

`LevelDBInstance` respects this for you — every instance for a path shares one handle. But that
guarantee only covers the managed layer. `LevelDB.open()` opens directly, outside the registry, so
mixing the two on one directory defeats it:

```kotlin
val managed = LevelDBInstance.builder("/data/db").build()
managed.use { putString("k", "v") }       // managed layer holds the handle

val raw = LevelDB.open("/data/db")          // second open on the same dir -> lock error
```

With `IdleDelayed`, the managed handle is sometimes open and sometimes not, so this kind of clash can
be intermittent — which is worse, because it looks like it works until it doesn't.

The rule is simple: **one owner per directory per process.** Pick `LevelDBInstance` or `LevelDB.open()`
for a given path and stay with it. If you need to touch the directory at the filesystem level (copy,
swap, restore) while the managed layer owns it, use [`useExclusively`](how-to.md#exclusive-access-for-backup-and-restore)
instead of opening a second handle.
