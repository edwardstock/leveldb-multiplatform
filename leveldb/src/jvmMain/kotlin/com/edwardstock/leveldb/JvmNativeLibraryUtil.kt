/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb

internal enum class JvmNativeArchitecture {
    LINUX_64,
    LINUX_ARM64,
    OSX_64,
    OSX_ARM64,
    WINDOWS_64,
    UNKNOWN,
}

internal object JvmNativeLibraryUtil {
    fun getArchitecture(): JvmNativeArchitecture {
        val os = System.getProperty("os.name")?.lowercase() ?: return JvmNativeArchitecture.UNKNOWN
        val arch = System.getProperty("os.arch")?.lowercase() ?: return JvmNativeArchitecture.UNKNOWN
        return when {
            os.contains("win") -> {
                if (isArm64(arch)) JvmNativeArchitecture.UNKNOWN
                else if (is64Bit(arch)) JvmNativeArchitecture.WINDOWS_64
                else JvmNativeArchitecture.UNKNOWN
            }

            os.contains("mac") || os.contains("darwin") -> {
                when {
                    isArm64(arch) -> JvmNativeArchitecture.OSX_ARM64
                    is64Bit(arch) -> JvmNativeArchitecture.OSX_64
                    else -> JvmNativeArchitecture.UNKNOWN
                }
            }

            os.contains("linux") || os.contains("nux") -> {
                when {
                    isArm64(arch) -> JvmNativeArchitecture.LINUX_ARM64
                    is64Bit(arch) -> JvmNativeArchitecture.LINUX_64
                    else -> JvmNativeArchitecture.UNKNOWN
                }
            }

            else -> JvmNativeArchitecture.UNKNOWN
        }
    }

    fun getArchDir(architecture: JvmNativeArchitecture): String? = when (architecture) {
        JvmNativeArchitecture.LINUX_64 -> "linux_64"
        JvmNativeArchitecture.LINUX_ARM64 -> "linux_arm64"
        JvmNativeArchitecture.OSX_64 -> "osx_64"
        JvmNativeArchitecture.OSX_ARM64 -> "osx_arm64"
        JvmNativeArchitecture.WINDOWS_64 -> "windows_64"
        JvmNativeArchitecture.UNKNOWN -> null
    }

    fun getPlatformLibraryName(architecture: JvmNativeArchitecture, libName: String): String? =
        when (architecture) {
            JvmNativeArchitecture.WINDOWS_64 -> "$libName.dll"
            JvmNativeArchitecture.OSX_64,
            JvmNativeArchitecture.OSX_ARM64 -> "lib$libName.dylib"

            JvmNativeArchitecture.LINUX_64,
            JvmNativeArchitecture.LINUX_ARM64 -> "lib$libName.so"

            JvmNativeArchitecture.UNKNOWN -> null
        }

    private fun isArm64(arch: String): Boolean =
        arch.contains("aarch64") || arch.contains("arm64")

    private fun is64Bit(arch: String): Boolean =
        arch.contains("64") || arch.contains("amd64") || arch.contains("x86_64")
}
