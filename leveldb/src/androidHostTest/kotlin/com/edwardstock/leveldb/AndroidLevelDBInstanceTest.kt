package com.edwardstock.leveldb

import android.content.Context
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.internal.levelDbLoadNativeLibrary
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Host-side coverage for the Android builder wiring — path resolution from [Context.filesDir].
 *
 * Real DB operations are deliberately not exercised: the Android JNI library only loads on a
 * device, so the shared engine, lifecycle and migration logic is covered by the JVM and native
 * test targets. What the Android wrapper uniquely owns is resolving the DB path under `filesDir`,
 * and that is what these tests pin.
 */
class AndroidLevelDBInstanceTest {

    private val nativeFacade = "com.edwardstock.leveldb.internal.PlatformUtils_androidKt"
    private lateinit var baseDir: File

    @BeforeTest
    fun setUp() {
        // The native loader fires from LevelDBInstance's companion init on the first build().
        // There is no Android .so on the host JVM, so stub the load — we never touch native ops.
        mockkStatic(nativeFacade)
        every { levelDbLoadNativeLibrary() } returns Unit
        baseDir = Files.createTempDirectory("ldb-android-host").toFile()
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(nativeFacade)
        baseDir.deleteRecursively()
    }

    private fun context(dir: File = baseDir): Context = mockk(relaxed = true) {
        every { filesDir } returns dir
    }

    @Test
    fun `builder resolves db path under filesDir`() {
        val instance = AndroidLevelDBInstance.builder(context(), "accounts").build()
        assertEquals(baseDir.resolve("accounts").absolutePath.toPath(), instance.path)
    }

    @Test
    fun `default db name is used when none is provided`() {
        val instance = AndroidLevelDBInstance.builder(context()).build()
        assertEquals(baseDir.resolve(LevelDB.DEFAULT_DBNAME).absolutePath.toPath(), instance.path)
    }

    @Test
    fun `dbName override recomputes the resolved path`() {
        val instance = AndroidLevelDBInstance.builder(context(), "first")
            .dbName("second")
            .build()
        assertEquals(baseDir.resolve("second").absolutePath.toPath(), instance.path)
    }

    @Test
    fun `baseDirectory File override relocates the db`() {
        val custom = Files.createTempDirectory("ldb-android-custom").toFile()
        try {
            val instance = AndroidLevelDBInstance.builder(context(), "db")
                .baseDirectory(custom)
                .build()
            assertEquals(custom.resolve("db").absolutePath.toPath(), instance.path)
        } finally {
            custom.deleteRecursively()
        }
    }

    @Test
    fun `baseDirectory Path override relocates the db`() {
        val custom = Files.createTempDirectory("ldb-android-custom-path").toFile()
        try {
            val instance = AndroidLevelDBInstance.builder(context(), "db")
                .baseDirectory(custom.absolutePath.toPath())
                .build()
            assertEquals(custom.resolve("db").absolutePath.toPath(), instance.path)
        } finally {
            custom.deleteRecursively()
        }
    }

    @Test
    fun `dsl builder applies the block before building`() {
        val instance = AndroidLevelDBInstance.builder(context(), "dsl") {
            dbName("dsl-renamed")
        }
        assertEquals(baseDir.resolve("dsl-renamed").absolutePath.toPath(), instance.path)
    }
}
