package com.edwardstock.leveldb.test

import com.edwardstock.leveldb.LevelDbByteArrayComparator
import java.util.NavigableMap
import java.util.TreeMap

internal actual class SortedBytesStore actual constructor() {
    private val map: NavigableMap<ByteArray, ByteArray> = TreeMap(LevelDbByteArrayComparator)

    actual val size: Int
        get() = map.size

    actual fun get(key: ByteArray): ByteArray? = map[key]?.copyOf()

    actual fun put(key: ByteArray, value: ByteArray) {
        map[key.copyOf()] = value.copyOf()
    }

    actual fun remove(key: ByteArray) {
        map.remove(key)
    }

    actual fun snapshotCopy(): SortedBytesStore {
        val copy = SortedBytesStore()
        for ((k, v) in map) {
            copy.put(k, v)
        }
        return copy
    }

    actual fun keysOrdered(): List<ByteArray> = map.keys.map { it.copyOf() }

    actual fun entriesOrdered(): List<Pair<ByteArray, ByteArray>> =
        map.entries.map { it.key.copyOf() to it.value.copyOf() }

    actual fun ceilingKey(key: ByteArray): ByteArray? = map.ceilingKey(key)?.copyOf()

    actual fun floorKey(key: ByteArray): ByteArray? = map.floorKey(key)?.copyOf()

    actual fun firstKeyOrNull(): ByteArray? = map.firstEntry()?.key?.copyOf()

    actual fun lastKeyOrNull(): ByteArray? = map.lastEntry()?.key?.copyOf()
}
