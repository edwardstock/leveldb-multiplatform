package com.edwardstock.leveldb

import com.edwardstock.leveldb.api.ValueAdapter
import com.edwardstock.leveldb.exception.LevelDBNoTypeAdapterException
import com.edwardstock.leveldb.internal.levelDbDefaultAdapters
import kotlin.reflect.KClass

class AdapterRegistry internal constructor(
    @PublishedApi internal val adapters: MutableMap<KClass<*>, ValueAdapter<out Any>>,
) {
    constructor() : this(levelDbDefaultAdapters().toMutableMap())

    fun copy(): AdapterRegistry = AdapterRegistry(adapters.toMutableMap())

    inline fun <reified T : Any> addAdapter(adapter: ValueAdapter<T>) =
        addAdapter(T::class, adapter)

    fun <T : Any> addAdapter(clazz: KClass<T>, adapter: ValueAdapter<T>) {
        adapters[clazz] = adapter
    }

    fun clearAdapters() {
        adapters.clear()
    }

    fun removeAdapter(clazz: KClass<*>) {
        adapters.remove(clazz)
    }

    inline fun <reified T : Any> removeAdapter() = removeAdapter(T::class)

    inline fun <reified T> findAdapter(): ValueAdapter<T>? {
        @Suppress("UNCHECKED_CAST")
        return adapters[T::class] as? ValueAdapter<T>
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findAdapter(clazz: KClass<T>): ValueAdapter<T>? {
        return adapters[clazz] as? ValueAdapter<T>
    }

    inline fun <reified T : Any> decode(value: ByteArray): T {
        return decode(value, T::class)
    }

    fun <T : Any> decode(value: ByteArray, clazz: KClass<T>): T {
        val adapter = findAdapter(clazz) ?: throw LevelDBNoTypeAdapterException(clazz)
        return adapter.decode(value)
    }

    inline fun <reified T : Any> encode(value: T): ByteArray = encode(value, T::class)

    fun <T : Any> encode(value: T, clazz: KClass<T>): ByteArray {
        val adapter = findAdapter(clazz) ?: throw LevelDBNoTypeAdapterException(clazz)
        return adapter.encode(value)
    }
}
