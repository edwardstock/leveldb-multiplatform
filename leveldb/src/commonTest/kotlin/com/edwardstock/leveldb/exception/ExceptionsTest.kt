package com.edwardstock.leveldb.exception

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExceptionsTest {
    @Test
    fun `exceptions keep messages`() {
        val message = "boom"
        assertEquals(message, LevelDBClosedException(message).message)
        assertEquals(message, LevelDBCorruptionException(message).message)
        assertEquals(message, LevelDBIOException(message).message)
        assertEquals(message, LevelDBIteratorNotValidException(message).message)
        assertEquals(message, LevelDBNotFoundException(message).message)
        assertEquals(message, LevelDBSnapshotOwnershipException(message).message)
    }

    /**
     * Pins the load-bearing fact about [LevelDBNoTypeAdapterException]: the offending type is
     * reported in the message by its [kotlin.reflect.KClass.qualifiedName] (NOT simpleName,
     * which would lose package disambiguation). We assert the message CONTAINS the fully
     * qualified name rather than an exact prose string, so a harmless reword of the surrounding
     * wording does not produce a false failure, while a regression that swaps qualifiedName for
     * simpleName (dropping "kotlin.") or stops reporting the type at all still goes RED.
     */
    @Test
    fun `no-type-adapter exception reports the fully qualified type name`() {
        val adapterMessage = LevelDBNoTypeAdapterException(String::class).message
        assertTrue(adapterMessage != null, "adapter exception must carry a message")
        // Full qualified name present -> catches a simpleName regression (which yields "String").
        assertContains(adapterMessage, "kotlin.String")
        // qualifiedName for kotlin.String is exactly "kotlin.String". A mutation that swaps to
        // simpleName ("String"), or to clazz.toString() (JVM: "class kotlin.String") changes the
        // exact tail and goes RED here even though both still CONTAIN "kotlin.String".
        assertTrue(
            adapterMessage.endsWith("kotlin.String"),
            "message must end with the bare qualifiedName, was: $adapterMessage"
        )
    }

    /**
     * Pins fix #3: every catchable exception type in the library must be a subtype of
     * [LevelDBException] so that callers can catch the whole hierarchy with a single
     * `catch (e: LevelDBException)`. If any of these types were re-parented to extend
     * [Exception]/[RuntimeException] directly (or some other base outside the hierarchy),
     * these assertions fail.
     */
    @Test
    fun `every exception is a LevelDBException`() {
        // Runtime reflective checks: re-parenting any type to plain Exception/RuntimeException (or
        // any base outside the hierarchy) makes [KClass.isInstance] return false here -> RED. Using
        // a Kotlin `is` operator instead would let the compiler fold each check to a constant
        // `true` (and, from language version 2.4, error out) rather than observe the mutation at
        // runtime.
        val base = LevelDBException::class
        val message = "boom"
        assertTrue(base.isInstance(LevelDBClosedException(message)), "LevelDBClosedException")
        assertTrue(base.isInstance(LevelDBCorruptionException(message)), "LevelDBCorruptionException")
        assertTrue(base.isInstance(LevelDBDecodingException(message)), "LevelDBDecodingException")
        assertTrue(base.isInstance(LevelDBIOException(message)), "LevelDBIOException")
        assertTrue(base.isInstance(LevelDBIteratorNotValidException(message)), "LevelDBIteratorNotValidException")
        assertTrue(base.isInstance(LevelDBMigrationException(message)), "LevelDBMigrationException")
        assertTrue(base.isInstance(LevelDBCorruptedMigrationException(message)), "LevelDBCorruptedMigrationException")
        assertTrue(base.isInstance(LevelDBConcurrencyException(message)), "LevelDBConcurrencyException")
        assertTrue(base.isInstance(LevelDBNotFoundException(message)), "LevelDBNotFoundException")
        assertTrue(base.isInstance(LevelDBSnapshotOwnershipException(message)), "LevelDBSnapshotOwnershipException")
        assertTrue(base.isInstance(LevelDBNoTypeAdapterException(String::class)), "LevelDBNoTypeAdapterException")
    }

    /**
     * Pins that [LevelDBException] is a CHECKED [Exception], NOT an unchecked [RuntimeException].
     *
     * The `is Exception` check alone is toothless against the most semantically meaningful
     * mutation -- re-parenting the base to `RuntimeException` -- because in Kotlin/Java
     * `RuntimeException` IS-A `Exception`, so `is Exception` would stay GREEN. The
     * [assertFalse] below is the load-bearing assertion: it goes RED the moment the base is
     * changed from `Exception(...)` to `RuntimeException(...)`, making the whole hierarchy
     * unchecked. Since every other type extends [LevelDBException], this guards the entire
     * hierarchy's checked-ness in one place.
     */
    @Test
    fun `LevelDBException is a checked Exception and not a RuntimeException`() {
        val ex = LevelDBException("boom")
        // Runtime reflective checks (Kotlin `is` would fold to compile-time constants and, from
        // language version 2.4, error out instead of catching the re-parent mutation at runtime).
        assertTrue(
            Exception::class.isInstance(ex),
            "LevelDBException must remain an Exception"
        )
        assertFalse(
            RuntimeException::class.isInstance(ex),
            "LevelDBException must stay a CHECKED Exception, not a RuntimeException"
        )
    }

    /**
     * Pins the base [LevelDBException] `(message, cause)` constructor: both the message and the
     * cause must be forwarded (the `cause` override at the base must surface the passed cause).
     * Catches a mutation that drops the cause from the base constructor or stops overriding
     * `cause`, which would otherwise slip through every subtype test that only uses single-arg
     * constructors.
     */
    @Test
    fun `base LevelDBException retains message and forwards cause`() {
        val cause = IllegalStateException("root failure")
        val ex = LevelDBException("wrapped", cause)
        assertEquals("wrapped", ex.message)
        assertSame(cause, ex.cause)
    }

    /**
     * Pins the EXACT direct parent of every type that is a direct child of [LevelDBException].
     *
     * The `every exception is a LevelDBException` test only checks `is LevelDBException`, which
     * stays transitively GREEN if a leaf is re-parented to a SIBLING inside the hierarchy
     * (e.g. `LevelDBConcurrencyException : LevelDBMigrationException`). That blind spot lets a
     * handler relying on `catch (LevelDBMigrationException)` silently start swallowing unrelated
     * concurrency/IO/corruption failures. The negative checks below pin that each direct child
     * is NOT an instance of a concrete sibling it must not inherit from.
     *
     * These use [KClass.isInstance] (a RUNTIME reflective check) rather than a Kotlin `is`
     * operator on purpose: with `is`, the compiler statically proves the operand can never be the
     * sibling type and folds the check to a constant `false` (and, from language version 2.4, makes
     * it a compile error). A re-parent mutation in production would then flip that compile-time
     * constant rather than be observed at runtime. `isInstance` is opaque to that analysis, so the
     * assertion is a genuine runtime guard: after a sibling re-parent, [KClass.isInstance] returns
     * `true` and the corresponding [assertFalse] goes RED.
     *
     * Note: [LevelDBCorruptedMigrationException] is deliberately the ONE type whose parent is
     * [LevelDBMigrationException] -- pinned separately in `corrupted migration is a migration
     * exception`.
     */
    @Test
    fun `direct children of LevelDBException do not inherit from a sibling`() {
        fun assertNotInstance(forbidden: KClass<*>, instance: LevelDBException, who: String) =
            assertFalse(
                forbidden.isInstance(instance),
                "$who must extend LevelDBException directly, not ${forbidden.simpleName}"
            )

        // Concurrency must NOT become a Migration (the previously-flagged killer re-parent).
        assertNotInstance(
            LevelDBMigrationException::class, LevelDBConcurrencyException("boom"), "LevelDBConcurrencyException"
        )
        // IO must NOT become a Corruption (the second flagged in-hierarchy re-parent).
        assertNotInstance(
            LevelDBCorruptionException::class, LevelDBIOException("boom"), "LevelDBIOException"
        )
        // Decoding must NOT become a Migration.
        assertNotInstance(
            LevelDBMigrationException::class, LevelDBDecodingException("boom"), "LevelDBDecodingException"
        )
        // Corruption must NOT become an IO exception.
        assertNotInstance(
            LevelDBIOException::class, LevelDBCorruptionException("boom"), "LevelDBCorruptionException"
        )
        // NotFound must NOT become a Corruption.
        assertNotInstance(
            LevelDBCorruptionException::class, LevelDBNotFoundException("boom"), "LevelDBNotFoundException"
        )
        // Closed must NOT become an IO exception.
        assertNotInstance(
            LevelDBIOException::class, LevelDBClosedException("boom"), "LevelDBClosedException"
        )
        // SnapshotOwnership must NOT become a Concurrency exception (both relate to ownership/threading).
        assertNotInstance(
            LevelDBConcurrencyException::class, LevelDBSnapshotOwnershipException("boom"), "LevelDBSnapshotOwnershipException"
        )
        // IteratorNotValid must NOT become a Corruption exception.
        assertNotInstance(
            LevelDBCorruptionException::class, LevelDBIteratorNotValidException("boom"), "LevelDBIteratorNotValidException"
        )
        // Migration itself must NOT become a Concurrency exception (sibling re-parent the other way).
        assertNotInstance(
            LevelDBConcurrencyException::class, LevelDBMigrationException("boom"), "LevelDBMigrationException"
        )
        // NoTypeAdapter must NOT become a Decoding exception (plausible-looking but wrong parent).
        assertNotInstance(
            LevelDBDecodingException::class, LevelDBNoTypeAdapterException(String::class), "LevelDBNoTypeAdapterException"
        )
    }

    /**
     * Pins the migration sub-hierarchy: [LevelDBCorruptedMigrationException] must remain a
     * subtype of [LevelDBMigrationException] (not just of [LevelDBException] directly), so a
     * handler that catches migration failures specifically still sees corruption failures.
     * Catches a mutation that flattens corrupted-migration to extend LevelDBException directly.
     */
    @Test
    fun `corrupted migration is a migration exception`() {
        // Runtime reflective checks (see rationale on `direct children ... sibling`): a flatten
        // mutation that re-parents CorruptedMigration straight to LevelDBException makes the first
        // assertion's isInstance return false -> RED. With a Kotlin `is` operator the compiler folds
        // this to a constant `true` and (from LV 2.4) errors out instead of observing the mutation.
        assertTrue(
            LevelDBMigrationException::class.isInstance(LevelDBCorruptedMigrationException("boom")),
            "LevelDBCorruptedMigrationException must remain a LevelDBMigrationException"
        )
        // and transitively still a LevelDBException
        assertTrue(
            LevelDBException::class.isInstance(LevelDBCorruptedMigrationException("boom")),
            "LevelDBCorruptedMigrationException must remain a LevelDBException"
        )
    }

    /**
     * Pins the default messages of the three [JvmOverloads] no-arg constructors. These defaults
     * are never exercised by the rest of the suite (every other test passes an explicit message),
     * so without this test a mutation that blanks/changes any default to "" or null slips through
     * GREEN. Each assertion goes RED if its corresponding default string literal is mutated.
     */
    @Test
    fun `default-message constructors carry their default message`() {
        assertEquals("This database has been closed!", LevelDBClosedException().message)
        assertEquals(
            "The snapshot is not owned by this database.",
            LevelDBSnapshotOwnershipException().message
        )
        assertEquals("Iterator is not valid.", LevelDBIteratorNotValidException().message)
    }

    /**
     * Pins cause propagation for the `(cause)`-only constructors of every multi-constructor type,
     * including [LevelDBCorruptedMigrationException] whose cause-only form delegates up through
     * [LevelDBMigrationException]. Each must retain the cause AND derive the message from
     * `cause.message`. Catches a mutation that drops the cause or stops forwarding cause.message
     * in any of these four cause-only paths.
     */
    @Test
    fun `cause-only constructors retain cause and derive message`() {
        val cause = IllegalStateException("root failure")

        val decoding = LevelDBDecodingException(cause)
        assertSame(cause, decoding.cause)
        assertEquals("root failure", decoding.message)

        val migration = LevelDBMigrationException(cause)
        assertSame(cause, migration.cause)
        assertEquals("root failure", migration.message)

        val concurrency = LevelDBConcurrencyException(cause)
        assertSame(cause, concurrency.cause)
        assertEquals("root failure", concurrency.message)

        val corruptedMigration = LevelDBCorruptedMigrationException(cause)
        assertSame(cause, corruptedMigration.cause)
        assertEquals("root failure", corruptedMigration.message)
    }

    /**
     * Pins that the `(message, cause)` two-arg constructors of ALL multi-constructor types keep
     * both fields. Without this, the sibling two-arg constructors of Decoding/Migration/
     * Concurrency are unverified for cause retention -- a mutation that calls `super(message)`
     * instead of `super(message, cause)` (silently dropping the cause while keeping the message)
     * would slip through. Each `assertSame(cause, ...)` goes RED on that mutation.
     */
    @Test
    fun `message-and-cause constructors retain both`() {
        val cause = IllegalStateException("root failure")
        val message = "wrapped"

        val decoding = LevelDBDecodingException(message, cause)
        assertEquals(message, decoding.message)
        assertSame(cause, decoding.cause)

        val migration = LevelDBMigrationException(message, cause)
        assertEquals(message, migration.message)
        assertSame(cause, migration.cause)

        val concurrency = LevelDBConcurrencyException(message, cause)
        assertEquals(message, concurrency.message)
        assertSame(cause, concurrency.cause)

        val corrupted = LevelDBCorruptedMigrationException(message, cause)
        assertEquals(message, corrupted.message)
        assertSame(cause, corrupted.cause)
        // Runtime reflective parentage checks (see rationale on `direct children ... sibling`).
        assertTrue(LevelDBMigrationException::class.isInstance(corrupted))
        assertTrue(LevelDBException::class.isInstance(corrupted))
    }

    /**
     * Pins that the single-arg `(message)` constructors do NOT fabricate a cause: a message-only
     * exception must report a null cause. Catches a mutation that wires a non-null cause into the
     * message-only path.
     */
    @Test
    fun `message-only constructors carry no cause`() {
        assertNull(LevelDBDecodingException("boom").cause)
        assertNull(LevelDBMigrationException("boom").cause)
        assertNull(LevelDBConcurrencyException("boom").cause)
        assertNull(LevelDBCorruptedMigrationException("boom").cause)
        assertNull(LevelDBCorruptionException("boom").cause)
    }
}
