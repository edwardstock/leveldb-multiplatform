# Getting started

This walks you through your first database end to end: open it, write a few values, read them back,
store a typed value, scan a range, and close up. By the end you'll have a working `LevelDBInstance`
and know the handful of calls you'll use every day.

You need a Kotlin Multiplatform (or plain JVM/Android) project and basic familiarity with coroutines.
That's it — the native library loads itself.

## 1. Add the dependency

```kotlin
dependencies {
    implementation("com.edwardstock.leveldb:leveldb:<version>")
}
```

## 2. Open a database

A `LevelDBInstance` is tied to one directory on disk. Build it once and keep it around — it manages
the underlying handle for you.

```kotlin
import com.edwardstock.leveldb.LevelDBInstance

val db = LevelDBInstance.builder("/path/to/db").build()
```

On Android, you usually want the database under the app's private files directory. Use
`AndroidLevelDBInstance`, which resolves the path for you:

```kotlin
import com.edwardstock.leveldb.AndroidLevelDBInstance

val db = AndroidLevelDBInstance.builder(context, dbName = "app.ldb").build()
```

Building the instance does not open the handle yet. The first `use {}` does.

## 3. Write and read

Operations run inside `use {}`. It's a `suspend` function, so call it from a coroutine. Inside the
block, `this` is the database, and the `putString` / `getString` extensions cover the common case:

```kotlin
import com.edwardstock.leveldb.api.getString
import com.edwardstock.leveldb.api.putString
import kotlinx.coroutines.runBlocking

runBlocking {
    db.use {
        putString("user:1:name", "Ada")
        putString("user:1:role", "admin")

        val name = getString("user:1:name")   // "Ada"
        println(name)
    }
}
```

`runBlocking` is just here to give the example a coroutine to run in. In a real app you already have
one — a `viewModelScope`, a `launch`, a suspend function up the call stack.

Deleting a key is the same idea. Either call `del`, or assign `null` — both mean "remove this key":

```kotlin
db.use {
    del("user:1:role")
    // equivalent:
    this["user:1:role"] = null
}
```

That bracket syntax is an operator. `this["k"] = "v"` writes, `this["k"]` reads raw bytes. Handy for
quick access; the named methods are clearer for typed values.

## 4. Store something other than a string

LevelDB stores bytes. The library converts common types for you through adapters, so you can put and
get an `Int`, `Long`, `Double`, `Boolean`, and so on directly:

```kotlin
import com.edwardstock.leveldb.api.getValue
import com.edwardstock.leveldb.api.putValue

db.use {
    putValue("user:1:logins", 42)

    val logins = getValue<Int>("user:1:logins")   // 42
    println(logins)
}
```

`putValue(key, null)` deletes the key, same as everywhere else. For your own types, register a custom
adapter — see [How-to guides](how-to.md#store-a-custom-type).

## 5. Scan a range

LevelDB keeps keys in sorted (lexicographic) order, which makes prefix scans cheap. The `user:1:`
prefix above wasn't an accident — here's how to read everything under it:

```kotlin
db.use {
    forEachPrefix("user:1:") { entry ->
        println("${entry.keyString()} = ${entry.valueString()}")
    }
}
```

`forEachPrefix` seeks to the prefix and walks forward until the keys stop matching, so it touches only
the rows you care about. There's also `forEachAll` for a full scan — use it sparingly, it reads the
whole database.

## 6. Close when you're done

`LevelDBInstance` closes the handle on its own once nobody is using it, so for most apps you don't
close anything. When you need a deterministic shutdown — a test, a service stopping — await it:

```kotlin
db.closeAndAwait()
```

## What you've got

You've covered the calls that make up most day-to-day use: `builder`, `use {}`, `putString` /
`getString`, typed `putValue` / `getValue`, and `forEachPrefix`. From here:

- [Concepts](concepts.md) explains why `use {}` is a coroutine, how the idle-close works, and when to
  reach for the raw `LevelDB.open()` instead.
- [How-to guides](how-to.md) has recipes for batches, snapshots, custom adapters, and backups.
- [Migrations](migrations.md) covers evolving your schema over time.
