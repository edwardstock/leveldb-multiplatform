/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

@file:OptIn(ExperimentalForeignApi::class)

package com.edwardstock.leveldb.utils

import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.UByteVarOf
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.reinterpret

internal class PinnedScope(
    private val pins: ArrayDeque<Pinned<*>> = ArrayDeque()
) : AutoCloseable {
    fun <T : Any> T.autoPin(): Pinned<T> = pin().also {
        pins.addLast(it)
    }

    fun ByteArray?.autoPinUByteArray(): CValuesRef<UByteVarOf<UByte>>? = if(this == null || isEmpty()) {
        null
    } else {
        autoPin().addressOf(0).reinterpret()
    }

    fun ByteArray?.autoPinByteArray(): CValuesRef<ByteVarOf<Byte>>? = if(this == null || isEmpty()) {
        null
    } else {
        autoPin().addressOf(0)
    }

    override fun close() {
        while (pins.isNotEmpty()) {
            pins.removeLast().unpin()
        }
    }
}

internal inline fun <R> pinScope(block: PinnedScope.()->R): R = PinnedScope().use(block)
