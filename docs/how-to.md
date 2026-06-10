# How-to guides

Short recipes for specific tasks. Each one assumes you already have a `LevelDBInstance` (see
[Getting started](getting-started.md)) and that you're inside a `use {}` block unless noted otherwise.

- [Store a custom type](#store-a-custom-type)
- [Scan keys by prefix or in full](#scan-keys-by-prefix-or-in-full)
- [Write several changes atomically](#write-several-changes-atomically)
- [Read a consistent view with a snapshot](#read-a-consistent-view-with-a-snapshot)
- [Exclusive access for backup and restore](#exclusive-access-for-backup-and-restore)
- [Recover a broken database](#recover-a-broken-database)

## Store a custom type

Primitives (`Int`, `Long`, `Double`, `Boolean`, and friends) already have adapters. For your own
types, implement `ValueAdapter` — it's just encode/decode to and from bytes:

```kotlin
import com.edwardstock.leveldb.api.ValueAdapter

data class User(val id: Int, val name: String)

class UserAdapter : ValueAdapter<User> {
    override fun encode(value: User): ByteArray =
        "${value.id}:${value.name}".encodeToByteArray()

    override fun decode(value: ByteArray): User {
        val (id, name) = value.decodeToString().split(":", limit = 2)
        return User(id.toInt(), name)
    }
}
```

Register it on the builder, then use `putValue` / `getValue` with your type:

```kotlin
val db = LevelDBInstance.builder("/path/to/db")
    .adapters { addAdapter(UserAdapter()) }
    .build()

db.use {
    putValue("user:1", User(1, "Ada"))
    val user = getValue<User>("user:1")   // User(1, "Ada")
}
```

Real apps usually back the adapter with a serializer (kotlinx.serialization, protobuf, JSON) instead
of hand-rolled string splitting. The interface is the same — encode to bytes, decode from bytes.

## Scan keys by prefix or in full

LevelDB stores keys in sorted order, so a prefix scan is cheap: it seeks straight to the prefix and
stops as soon as the keys stop matching.

```kotlin
db.use {
    forEachPrefix("user:") { entry ->
        println("${entry.keyString()} -> ${entry.valueString()}")
    }
}
```

Each `entry` can hand you the key and value as bytes, as a string, or as a typed value
(`entry.valueT<User>()`). There are also shortcut overloads when you only want one side —
`forEachPrefix` has siblings like `forEachAllKeyString` and `forEachAllValueString` for full scans.

A full scan reads the entire database, so reach for it deliberately:

```kotlin
db.use {
    forEachAll { entry -> /* ... */ }
}
```

`forEachAll` defaults to `fillCache = false` so a full sweep doesn't evict your hot data from
LevelDB's block cache. Pass `fillCache = true` only if you actually want to warm the cache.

## Write several changes atomically

A write batch applies all of its operations together or not at all. Use `withBatch` inside `use {}`;
the receiver is the batch:

```kotlin
db.use {
    withBatch(sync = true) {
        putString("user:1:name", "Ada")
        putValue("user:1:logins", 0)
        del("user:1:legacy")
    }
}
```

`sync = true` flushes to disk before returning, so the data survives a system crash. `sync = false`
is faster but only guarantees durability against a process crash, not a power loss. As with single
writes, a `null` value in a batch put is a delete.

## Read a consistent view with a snapshot

A snapshot is a read-only view frozen at a point in time. Reads against it ignore later writes, which
is what you want when you need several reads to agree:

```kotlin
db.use {
    putString("k", "v1")

    val snapshot = obtainSnapshot()
    putString("k", "v2")            // changes the live database...

    println(getString("k", snapshot))  // "v1" — the snapshot still sees the old value
    println(getString("k"))            // "v2" — live read

    snapshot.close()
}
```

Close the snapshot when you're done with it. A snapshot belongs to the database that created it;
passing it to a different database throws.

> Snapshots and iterators are not thread-safe. Don't share one across threads.

## Exclusive access for backup and restore

Copying, swapping, or restoring the database directory while it's in use is dangerous. `useExclusively`
gives you a safe window: it blocks new `use {}` calls for the path, waits for active ones to drain,
and lets you touch the directory.

```kotlin
instance.useExclusively {
    // New use {} for this path is blocked; active ones have finished.
    // Safe to copy / swap / restore the directory here.

    open {
        // Need database access during exclusivity? Use open {}, not use {}.
        putString("backup-marker", "done")
    }
}
```

The rules that keep this from deadlocking:

- Don't call `useExclusively {}` from inside `use {}` for the same path.
- Don't nest `useExclusively {}` on the same instance.
- Inside the exclusive block, reach the database with `open {}`, never `use {}` — `useExclusively`
  is waiting for `use {}` to drain, so calling `use {}` there waits on yourself.
- To run concurrent work inside the block, use the provided scope and call `open {}` from its child
  coroutines.

This is also the correct way to touch the directory at the filesystem level while the managed layer
owns the handle — see [one owner per path](concepts.md#one-owner-per-path).

## Recover a broken database

A crash, a bad filesystem state, or a stale lock can leave a database that won't open. As a last
resort, ask LevelDB to rebuild what it can:

```kotlin
LevelDB.repair("/path/to/db")
```

This calls `leveldb::RepairDB()` under the hood. It can lose data, so treat it as a recovery tool, not
routine maintenance — and tell your users when it runs.

To wipe a database directory completely:

```kotlin
LevelDB.destroy("/path/to/db")
```
