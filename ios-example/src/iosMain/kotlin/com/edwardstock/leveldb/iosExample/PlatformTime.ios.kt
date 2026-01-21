@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.edwardstock.leveldb.iosExample

import kotlinx.cinterop.alloc
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.convert
import platform.posix.CLOCK_REALTIME
import platform.posix.clock_gettime
import platform.posix.timespec

internal actual fun nowMillis(): Long = memScoped {
    val ts = alloc<timespec>()
    clock_gettime(CLOCK_REALTIME.convert(), ts.ptr)
    ts.tv_sec * 1000L + (ts.tv_nsec / 1_000_000L)
}
