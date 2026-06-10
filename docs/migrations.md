# Migrations

LevelDB has no schema of its own. This library adds a versioning layer on top so you can evolve your
data over time: a version number stored inside the database, plus a list of steps that move it from
one version to the next.

## The model

You describe the target state with a `LevelDBSchema` and attach it to the instance:

```kotlin
import com.edwardstock.leveldb.migration.LevelDBSchema
import com.edwardstock.leveldb.migration.SoftMigration

val schema = LevelDBSchema(
    targetVersion = 2,
    migrations = listOf(
        SoftMigration(0, 1),        // additive change, no data rewrite
        RewriteUserValues(1, 2),    // your own step (below)
    ),
)

val db = LevelDBInstance.builder("/path/to/db")
    .schema(schema)
    .build()
```

The stored version starts at `0` for a fresh database. When the schema's `targetVersion` is higher,
the library runs the steps in order to close the gap.

### Migration steps are linear

The plan is strictly linear: one step per hop, `N -> N+1`. To reach version 3 from 0 you need steps
`0->1`, `1->2`, and `2->3` — exactly one each. A missing hop or a non-linear jump fails with
`LevelDBMigrationException` rather than guessing. (Silently bumping the version without a declared step
is how you ship corruption to production.)

A step is a `LevelDBMigration`:

```kotlin
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.forEachPrefix
import com.edwardstock.leveldb.migration.LevelDBMigration

class RewriteUserValues(
    override val from: Int,
    override val to: Int,
) : LevelDBMigration {
    override val name = "Rewrite user values to v2"

    override suspend fun migrate(db: LevelDB) {
        db.withBatch(sync = true) {
            db.forEachPrefix("user:") { entry ->
                put(entry.keyBytes(), transform(entry.valueBytes()))
            }
        }
    }
}
```

For an additive change that needs no data rewrite — new keys, new optional fields, a lazily-rebuilt
index — use `SoftMigration(from, to)`. It only bumps the version, which keeps the version graph legal
without touching data.

### Write steps so they can re-run

A migration can be interrupted partway — process death, cancellation, an I/O error. The next run
retries the same step, so write steps to be idempotent:

- rebuild derived keys from a source of truth rather than mutating in place
- write the new key first, then delete the old one
- make writes conditional ("if already migrated, skip")
- prefer existence checks over trusting "this version implies this key exists"

## When migrations run

### Automatically (default)

With `migrateAutomatically = true` (the default), the first `use {}` that finds the version out of
date runs the migration before your block executes. It runs under path exclusivity: new `use {}` calls
for the path wait, active ones drain first, and normal access resumes once the database is migrated
and reopened.

### Manually

Set `migrateAutomatically = false` to take control, then run it yourself:

```kotlin
val schema = LevelDBSchema(
    targetVersion = 2,
    migrateAutomatically = false,
    migrations = listOf(/* ... */),
)

// later, at a moment you choose:
instance.migrateIfNeeded()
```

## Crash safety

Migration tracks progress with an in-database marker (`inProgressKey`) alongside the version:

1. Before a step: write `inProgress = step.to`.
2. After the step succeeds: set `version = step.to`.
3. After the whole migration succeeds: delete the marker.

So after a crash mid-step the next run sees `version = N` and `inProgress = N+1`, and retries that
step. Any other combination — an `inProgress` that's neither `version + 1` nor the target — is treated
as invalid and fails rather than charging ahead.

## The failed-migration guard

If a step throws, the instance records the failure **in memory** and stops retrying for the rest of
the process: later `use {}` calls throw `LevelDBCorruptedMigrationException`. This prevents an infinite
retry loop where a deterministic failure runs on every app start.

The guard does not survive a full restart. To retry after your own recovery logic, clear it
explicitly:

```kotlin
instance.migrateIfNeeded(ignorePreviousFailure = true)
```

Use that escape hatch carefully. If you always reset the failure, a migration that keeps failing will
keep looping.

## Safety policies

The `safety` setting controls how much of a safety net the migration runs with:

```kotlin
import com.edwardstock.leveldb.migration.LevelDBMigrationSafetyPolicy

val schema = LevelDBSchema(
    targetVersion = 2,
    safety = LevelDBMigrationSafetyPolicy.BACKUP_DIR,
    migrations = listOf(/* ... */),
)
```

| Policy | What it does | Cost |
|---|---|---|
| `NONE` (default) | Migrates the live database in place. | Fastest, no safety net. |
| `BACKUP_DIR` | Copies the database to a backup directory first, migrates in place, restores the backup on failure. | Extra disk and copy time. |
| `STAGING_DB` | Migrates a staging copy, then swaps directories; rolls the swap back on failure. | Most overhead, strongest rollback. |

Pick based on how much you'd regret a half-finished migration. For small, additive changes `NONE` is
usually fine; for a risky data rewrite on important data, `BACKUP_DIR` or `STAGING_DB` buys you a way
back.

## Full example

```kotlin
val schema = LevelDBSchema(
    targetVersion = 2,
    safety = LevelDBMigrationSafetyPolicy.BACKUP_DIR,
    migrations = listOf(
        SoftMigration(0, 1),
        RewriteUserValues(1, 2),
    ),
)

val db = LevelDBInstance.builder("/path/to/db")
    .schema(schema)
    .build()

// The first use {} migrates 0 -> 1 -> 2 if needed, then runs your block.
db.use {
    val user = getValue<User>("user:1")
}
```
