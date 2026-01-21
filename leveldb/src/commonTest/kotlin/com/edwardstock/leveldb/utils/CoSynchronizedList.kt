/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CoSynchronizedList<T>(
    private val container: MutableList<T> = mutableListOf()
) {
    private val mutex = Mutex()

    /**
     * Returns a new immutable list with the same content
     */
    fun toList(): List<T> = container.toList()

    /**
     * Returns a new mutable list with the same content
     */
    fun toMutableList(): MutableList<T> = container.toMutableList()

    suspend operator fun plusAssign(element: T) {
        add(element)
    }

    suspend operator fun minusAssign(element: T) {
        remove(element)
    }

    suspend fun <R> withLock(block: suspend MutableList<T>.() -> R): R = mutex.withLock {
        container.block()
    }

    suspend fun get(index: Int): T = mutex.withLock {
        container[index]
    }

    suspend fun set(index: Int, element: T) = mutex.withLock {
        container[index] = element
    }

    suspend fun add(element: T) = mutex.withLock {
        container.add(element)
    }

    suspend fun remove(element: T): Boolean = mutex.withLock {
        container.remove(element)
    }

    suspend fun clear() = mutex.withLock {
        container.clear()
    }

    suspend fun contains(element: T): Boolean = mutex.withLock {
        container.contains(element)
    }

    suspend fun size(): Int = mutex.withLock {
        container.size
    }

    suspend fun isEmpty(): Boolean = mutex.withLock {
        container.isEmpty()
    }

    suspend fun isNotEmpty(): Boolean = mutex.withLock {
        container.isNotEmpty()
    }
}
