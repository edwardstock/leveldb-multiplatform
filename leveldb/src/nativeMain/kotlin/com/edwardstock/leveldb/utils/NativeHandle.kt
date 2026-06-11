/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

@file:OptIn(ExperimentalForeignApi::class)

package com.edwardstock.leveldb.utils

import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

abstract class NativeHandle<T : CPointed> internal constructor(
    internal var ptr: CPointer<T>?,
) : AutoCloseable {
    val isValid: Boolean
        get() = ptr != null

    fun requirePtr() = ptr ?: error("Handle is invalid")

    override fun close() {
        closeInternal()
    }

    protected abstract fun closeInternal()
}
