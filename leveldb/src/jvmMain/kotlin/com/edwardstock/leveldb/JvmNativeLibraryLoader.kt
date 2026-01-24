/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb

import java.net.URL
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

internal object JvmNativeLibraryLoader {
    private const val JNI_PATH_PROPERTY = "leveldb.jni.path"
    private const val JNI_TMPDIR_PROPERTY = "leveldb.jni.tmpdir"
    private const val JNI_TMPDIR_DEFAULT = "leveldb-jni"

    private val loaded = AtomicBoolean(false)

    fun load(libName: String) {
        if (loaded.get()) return
        synchronized(this) {
            if (loaded.get()) return
            val overridePath = System.getProperty(JNI_PATH_PROPERTY)
            if (!overridePath.isNullOrBlank()) {
                loadPath(overridePath)
                loaded.set(true)
                return
            }
            val extracted = extractLibrary(libName)
            loadPath(extracted.toAbsolutePath().toString())
            loaded.set(true)
        }
    }

    private fun loadPath(path: String) {
        try {
            System.load(path)
        } catch (e: UnsatisfiedLinkError) {
            if (e.message?.contains("already loaded in another classloader") == true) {
                return
            }
            throw e
        }
    }

    private fun extractLibrary(libName: String): Path {
        val architecture = JvmNativeLibraryUtil.getArchitecture()
        val archDir = JvmNativeLibraryUtil.getArchDir(architecture)
            ?: throw UnsatisfiedLinkError("Unsupported platform for native library")
        val mappedName = JvmNativeLibraryUtil.getPlatformLibraryName(architecture, libName)
            ?: throw UnsatisfiedLinkError("Unsupported platform for native library")
        val resourcePath = "natives/$archDir/$mappedName"
        val resourceUrl = JvmNativeLibraryLoader::class.java.classLoader.getResource(resourcePath)
            ?: throw UnsatisfiedLinkError("Native library resource not found: $resourcePath")
        val hash = sha256Hex(resourceUrl)

        val tmpBase = System.getProperty("java.io.tmpdir") ?: "."
        val tmpDirName = System.getProperty(JNI_TMPDIR_PROPERTY, JNI_TMPDIR_DEFAULT)
        val targetDir = Paths.get(tmpBase, tmpDirName, archDir, hash)
        Files.createDirectories(targetDir)

        val target = targetDir.resolve(mappedName)
        val lockFile = targetDir.resolve(".extract.lock")
        FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                if (Files.exists(target) && Files.size(target) > 0L) {
                    return target
                }
                val tempFile = Files.createTempFile(targetDir, mappedName, ".tmp")
                resourceUrl.openStream().use { input ->
                    Files.newOutputStream(tempFile, StandardOpenOption.WRITE).use { output ->
                        input.copyTo(output)
                    }
                }
                moveFile(tempFile, target)
            }
        }
        return target
    }

    private fun moveFile(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sha256Hex(url: URL): String {
        val digest = MessageDigest.getInstance("SHA-256")
        url.openStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val bytes = digest.digest()
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            if (v < 16) hex.append('0')
            hex.append(v.toString(16))
        }
        return hex.toString()
    }
}
