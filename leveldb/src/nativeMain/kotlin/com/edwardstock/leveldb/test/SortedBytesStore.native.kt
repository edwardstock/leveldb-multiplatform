package com.edwardstock.leveldb.test

import com.edwardstock.leveldb.LevelDbByteArrayComparator

internal actual class SortedBytesStore actual constructor() {
    private data class Entry(val key: ByteArray, val value: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (this::class != other?.let { it::class }) return false

            other as Entry

            if (!key.contentEquals(other.key)) return false
            if (!value.contentEquals(other.value)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = key.contentHashCode()
            result = 31 * result + value.contentHashCode()
            return result
        }
    }

    private val entries = mutableListOf<Entry>()

    actual val size: Int
        get() = entries.size

    actual fun get(key: ByteArray): ByteArray? {
        val idx = indexOfKey(key)
        return if (idx >= 0) entries[idx].value.copyOf() else null
    }

    actual fun put(key: ByteArray, value: ByteArray) {
        val k = key.copyOf()
        val v = value.copyOf()
        val idx = indexOfKey(k)
        if (idx >= 0) {
            entries[idx] = Entry(entries[idx].key, v)
            return
        }
        val insertAt = insertionPointFor(k)
        entries.add(insertAt, Entry(k, v))
    }

    actual fun remove(key: ByteArray) {
        val idx = indexOfKey(key)
        if (idx >= 0) entries.removeAt(idx)
    }

    actual fun snapshotCopy(): SortedBytesStore {
        val copy = SortedBytesStore()
        for (e in entries) {
            copy.put(e.key, e.value)
        }
        return copy
    }

    actual fun keysOrdered(): List<ByteArray> = entries.map { it.key.copyOf() }

    actual fun entriesOrdered(): List<Pair<ByteArray, ByteArray>> =
        entries.map { it.key.copyOf() to it.value.copyOf() }

    actual fun ceilingKey(key: ByteArray): ByteArray? {
        if (entries.isEmpty()) return null
        val insertAt = insertionPointFor(key)
        if (insertAt >= entries.size) return null
        return entries[insertAt].key.copyOf()
    }

    actual fun floorKey(key: ByteArray): ByteArray? {
        if (entries.isEmpty()) return null
        val idx = indexOfKey(key)
        return when {
            idx >= 0 -> entries[idx].key.copyOf()
            else -> {
                val insertAt = insertionPointFor(key)
                val floorIdx = insertAt - 1
                if (floorIdx < 0) null else entries[floorIdx].key.copyOf()
            }
        }
    }

    actual fun firstKeyOrNull(): ByteArray? = entries.firstOrNull()?.key?.copyOf()

    actual fun lastKeyOrNull(): ByteArray? = entries.lastOrNull()?.key?.copyOf()

    private fun indexOfKey(key: ByteArray): Int {
        var low = 0
        var high = entries.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val cmp = LevelDbByteArrayComparator.compare(entries[mid].key, key)
            when {
                cmp < 0 -> low = mid + 1
                cmp > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun insertionPointFor(key: ByteArray): Int {
        var low = 0
        var high = entries.size
        while (low < high) {
            val mid = (low + high) ushr 1
            val cmp = LevelDbByteArrayComparator.compare(entries[mid].key, key)
            if (cmp < 0) low = mid + 1 else high = mid
        }
        return low
    }
}
