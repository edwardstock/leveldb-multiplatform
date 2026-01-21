/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb

import com.edwardstock.leveldb.exception.LevelDBNoTypeAdapterException
import okio.FileSystem
import okio.SYSTEM
import kotlin.reflect.KClass

/**
 * Specifies a configuration to open the database with
 *
 * @param createIfMissing If true, the database will be created if it is missing
 * @param paranoidChecks If true, the implementation will do aggressive checking of the
 * data it is processing and will stop early if it detects any
 * errors.  This may have unforeseen ramifications: for example, a
 * corruption of one DB entry may cause a large number of entries to
 * become unreadable or for the entire DB to become unopenable
 * @param cacheSize Maximum cache size for fillCache parameter
 * @param blockSize Approximate size of user data packed per block
 * Note that the block size specified here corresponds to uncompressed data
 * The actual size of the unit read from disk may be smaller if
 * compression is enabled.  This parameter can be changed dynamically
 * @param writeBufferSize Parameters that affect performance
 * Amount of data to build up in memory (backed by an unsorted log
 * on disk) before converting to a sorted on-disk file
 *
 * Larger values increase performance, especially during bulk loads
 * Up to two write buffers may be held in memory at the same time,
 * so you may wish to adjust this parameter to control memory usage
 * Also, a larger write buffer will result in a longer recovery time
 * the next time the database is opened
 *
 * @param adapters data mapper for user types
 */
data class LevelDBConfig(
    var createIfMissing: Boolean = true,
    var paranoidChecks: Boolean = false,
    var cacheSize: Int = 0,
    var blockSize: Int = 0,
    var writeBufferSize: Int = 0,
    var adapters: MutableMap<KClass<*>, ValueAdapter<out Any>> = levelDbDefaultAdapters(),
    val fileSystem: FileSystem = FileSystem.SYSTEM,
) {

    inline fun <reified T : Any> addAdapter(adapter: ValueAdapter<T>) {
        adapters[T::class] = adapter
    }

    fun <T : Any> addAdapter(clazz: KClass<T>, adapter: ValueAdapter<T>) {
        adapters[clazz] = adapter
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> findAdapter(): ValueAdapter<T>? {
        return adapters[T::class] as? ValueAdapter<T>
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> findAdapter(clazz: KClass<T>): ValueAdapter<T>? {
        return adapters[clazz] as? ValueAdapter<T>
    }

    inline fun <reified T> convertT(key: String, value: String): T {
        return findAdapter<T>()?.decode(key.encodeToByteArray(), value.encodeToByteArray())
            ?: throw LevelDBNoTypeAdapterException(T::class)
    }

    fun <T : Any> convertT(key: String, value: String, clazz: KClass<T>): T {
        return findAdapter(clazz)?.decode(key.encodeToByteArray(), value.encodeToByteArray())
            ?: throw LevelDBNoTypeAdapterException(clazz)
    }

    inline fun <reified T : Any> convertFromT(value: T): String {
        return findAdapter<T>()?.encode(value)?.toString()
            ?: throw LevelDBNoTypeAdapterException(T::class)
    }

    fun <T : Any> convertFromT(value: T, clazz: KClass<T>): String {
        return findAdapter(clazz)?.encode(value)?.toString()
            ?: throw LevelDBNoTypeAdapterException(clazz)
    }
}
