package com.edwardstock.leveldb.api

import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.exception.LevelDBException
import com.edwardstock.leveldb.impl.NativeLevelDB
import okio.Path
import kotlin.reflect.KClass

// Iterators
fun LevelDB.iterator(): LevelDBIterator = iterator(true)
fun LevelDB.iterator(fillCache: Boolean): LevelDBIterator = iterator(fillCache, null)
fun LevelDB.iterator(snapshot: Snapshot): LevelDBIterator = iterator(true, snapshot)

// Properties
fun LevelDB.getPropertyBytes(key: String): ByteArray? = getPropertyBytes(key.encodeToByteArray())
fun LevelDB.getPropertyString(key: ByteArray): String? = getPropertyBytes(key)?.decodeToString()
fun LevelDB.getPropertyString(key: String): String? = getPropertyString(key.encodeToByteArray())

// Delete
fun LevelDB.del(key: String, sync: Boolean = false) = del(key.encodeToByteArray(), sync)

// Get values
fun LevelDB.getBytes(key: String, snapshot: Snapshot? = null): ByteArray? = getBytes(key.encodeToByteArray(), snapshot)

inline fun <reified T : Any> LevelDB.getValue(key: String, snapshot: Snapshot? = null): T? = getValue(key, T::class, snapshot)
inline fun <reified T : Any> LevelDB.getValue(key: ByteArray, snapshot: Snapshot? = null): T? = getValue(key, T::class, snapshot)
fun <T : Any> LevelDB.getValue(key: String, clazz: KClass<T>, snapshot: Snapshot? = null): T? = getValue(key.encodeToByteArray(), clazz, snapshot)
fun <T : Any> LevelDB.getValue(key: ByteArray, clazz: KClass<T>, snapshot: Snapshot? = null): T? = getBytes(key, snapshot)?.let {
    config.adapterRegistry.decode(it, clazz)
}

fun LevelDB.getString(key: ByteArray, snapshot: Snapshot? = null): String? = getBytes(key, snapshot)?.decodeToString()
fun LevelDB.getString(key: String, snapshot: Snapshot? = null): String? = getString(key.encodeToByteArray(), snapshot)

// Put values
fun LevelDB.putBytes(key: String, value: ByteArray?, sync: Boolean = false) = putBytes(key.encodeToByteArray(), value, sync)

fun <T : Any> LevelDB.putValue(key: ByteArray, value: T?, clazz: KClass<T>, sync: Boolean = false) {
    if (value == null) {
        del(key, sync)
    } else {
        putBytes(key, config.adapterRegistry.encode(value, clazz), sync)
    }
}

fun <T : Any> LevelDB.putValue(key: String, value: T?, clazz: KClass<T>, sync: Boolean = false) =
    putValue(key.encodeToByteArray(), value, clazz, sync)

inline fun <reified T : Any> LevelDB.putValue(key: String, value: T?, sync: Boolean = false) =
    putValue(key.encodeToByteArray(), value, T::class, sync)

inline fun <reified T : Any> LevelDB.putValue(key: ByteArray, value: T?, sync: Boolean = false) = putValue(key, value, T::class, sync)

fun LevelDB.putString(key: ByteArray, value: String?, sync: Boolean = false) = putBytes(key, value?.encodeToByteArray(), sync)
fun LevelDB.putString(key: String, value: String?, sync: Boolean = false) = putString(key.encodeToByteArray(), value, sync)

// Operators
operator fun LevelDB.get(key: String, snapshot: Snapshot? = null): ByteArray? = getBytes(key.encodeToByteArray(), snapshot)
operator fun LevelDB.get(key: ByteArray, snapshot: Snapshot? = null): ByteArray? = getBytes(key, snapshot)

operator fun LevelDB.set(key: ByteArray, value: ByteArray?) = putBytes(key, value)
operator fun LevelDB.set(key: String, value: ByteArray?) = putBytes(key.encodeToByteArray(), value)
operator fun LevelDB.set(key: String, value: String?) = putString(key, value)
operator fun LevelDB.set(key: ByteArray, value: String?) = putString(key, value)
inline operator fun <reified T : Any> LevelDB.set(key: ByteArray, value: T?) = putValue(key, value, T::class)
inline operator fun <reified T : Any> LevelDB.set(key: String, value: T?) = putValue(key, value, T::class)

// delete operators
// usage: db["something"] = null
operator fun LevelDB.set(key: String, value: Nothing?) = del(key.encodeToByteArray(), false)
operator fun LevelDB.set(key: ByteArray, value: Nothing?) = del(key, false)

// WriteBatch helpers
fun WriteBatch.putBytes(key: String, value: ByteArray?) = put(key.encodeToByteArray(), value)
fun WriteBatch.del(key: String) = del(key.encodeToByteArray())

fun WriteBatch.putString(key: String, value: String?) =
    put(key.encodeToByteArray(), value?.encodeToByteArray())

fun <T : Any> WriteBatch.putValue(
    key: ByteArray,
    value: T?,
    clazz: KClass<T>,
) {
    if (value == null) {
        del(key)
        return
    }

    put(key, config.adapterRegistry.encode(value, clazz))
}

fun <T : Any> WriteBatch.putValue(
    key: String,
    value: T?,
    clazz: KClass<T>,
) = putValue(key.encodeToByteArray(), value, clazz)

inline fun <reified T : Any> WriteBatch.putValue(
    key: String,
    value: T?,
) = putValue(key.encodeToByteArray(), value, T::class)

inline fun <reified T : Any> WriteBatch.putValue(
    key: ByteArray,
    value: T?,
) = putValue(key, value, T::class)

class LevelDBEntryView internal constructor(
    private val iterator: LevelDBIterator,
) {
    fun keyBytes(): ByteArray = iterator.key()
    fun keyString(): String = iterator.keyString()

    fun valueBytes(): ByteArray = iterator.value()
    fun valueString(): String = iterator.valueString()

    fun <T : Any> valueT(clazz: KClass<T>): T = iterator.valueT(clazz)

    inline fun <reified T : Any> valueT(): T = valueT(T::class)
}

private inline fun forEachAllInternal(
    iteratorProvider: () -> LevelDBIterator,
    block: (LevelDBEntryView) -> Unit,
) {
    iteratorProvider().use { iterator ->
        val entryView = LevelDBEntryView(iterator)

        iterator.seekToFirst()
        while (iterator.isValid) {
            block(entryView)
            iterator.next()
        }
    }
}

/**
 * Iterates over every entry in the database.
 *
 * Use with care — this is a full scan. [fillCache] defaults to `false` so the scan does not evict
 * the working set from LevelDB's block cache; pass `true` only if you intend to warm the cache.
 */
fun LevelDB.forEachAll(fillCache: Boolean = false, block: (LevelDBEntryView) -> Unit) {
    forEachAllInternal(
        iteratorProvider = { iterator(fillCache = fillCache) },
        block = block,
    )
}

/**
 * Convenience wrappers (no extra allocations besides what you request via conversions).
 */

fun LevelDB.forEachAllKeyBytes(block: (ByteArray) -> Unit) {
    forEachAll { entry -> block(entry.keyBytes()) }
}

fun LevelDB.forEachAllKeyString(block: (String) -> Unit) {
    forEachAll { entry -> block(entry.keyString()) }
}

fun LevelDB.forEachAllValueBytes(block: (ByteArray) -> Unit) {
    forEachAll { entry -> block(entry.valueBytes()) }
}

fun LevelDB.forEachAllValueString(block: (String) -> Unit) {
    forEachAll { entry -> block(entry.valueString()) }
}

inline fun <reified T : Any> LevelDB.forEachAllValueT(crossinline block: (T) -> Unit) {
    forEachAll { entry -> block(entry.valueT<T>()) }
}

inline fun <reified T : Any> LevelDB.forEachAllKeyStringValueT(crossinline block: (String, T) -> Unit) {
    forEachAll { entry -> block(entry.keyString(), entry.valueT<T>()) }
}

internal fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (prefix.size > size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i]) return false
    }
    return true
}

/**
 * Range scan by prefix:
 * seek(prefix) then iterate forward while key starts with prefix.
 *
 * Complexity: ~O(logN + K)
 */
fun LevelDB.forEachPrefix(
    prefix: ByteArray,
    fillCache: Boolean = false,
    block: (LevelDBEntryView) -> Unit,
) {
    iterator(fillCache = fillCache).use { it ->
        val view = LevelDBEntryView(it)
        it.seek(prefix)
        while (it.isValid) {
            val k = it.key()
            if (!k.startsWith(prefix)) break
            block(view)
            it.next()
        }
    }
}

fun LevelDB.forEachPrefix(
    prefix: String,
    fillCache: Boolean = false,
    block: (LevelDBEntryView) -> Unit,
) = forEachPrefix(prefix.encodeToByteArray(), fillCache, block)

fun LevelDB.Companion.open(path: String, config: LevelDBInstanceConfig = LevelDBInstanceConfig()): LevelDB {
    return NativeLevelDB(filePath = path, config = config)
}

fun LevelDB.Companion.open(path: Path, config: LevelDBInstanceConfig = LevelDBInstanceConfig()): LevelDB = open(
    path = path.normalized().toString(),
    config = config
)

/**
 * Destroys the contents of a LevelDB database
 * @param path the path to the database
 * @throws com.edwardstock.leveldb.exception.LevelDBException
 * @see com.edwardstock.leveldb.impl.NativeLevelDB.destroy
 */
@Throws(LevelDBException::class)
fun LevelDB.Companion.destroy(path: String) {
    NativeLevelDB.destroy(path)
}

/**
 * If a DB cannot be opened, you may attempt to call this method to resurrect as much of the contents of the
 * database as possible. Some data may be lost, so be careful when calling this function on a database that contains
 * important information
 * @param path the path to the database
 * @throws com.edwardstock.leveldb.exception.LevelDBException
 * @see com.edwardstock.leveldb.impl.NativeLevelDB.repair
 */
@Throws(LevelDBException::class)
fun LevelDB.Companion.repair(path: String) {
    NativeLevelDB.repair(path)
}
