package com.edwardstock.leveldb.impl

import com.edwardstock.leveldb.api.LevelDBIterator
import kotlin.reflect.KClass

internal class NoOpLevelDBIterator : LevelDBIterator {
    override val isValid: Boolean = true

    override fun seekToFirst() = Unit
    override fun seekToLast() = Unit
    override fun seek(key: ByteArray) = Unit
    override fun next() = Unit
    override fun previous() = Unit
    override fun key(): ByteArray = byteArrayOf()
    override fun value(): ByteArray = byteArrayOf()
    override fun <T : Any> valueT(clazz: KClass<T>): T {
        error("Not yet implemented")
    }

    override val isClosed: Boolean = false
    override fun close() = Unit
}
