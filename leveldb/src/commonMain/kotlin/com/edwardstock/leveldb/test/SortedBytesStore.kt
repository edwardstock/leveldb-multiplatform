package com.edwardstock.leveldb.test

/**
 * Minimal sorted key/value store used by in-memory test doubles.
 *
 * Implemented with platform-specific data structures (TreeMap on JVM/Android,
 * sorted list + binary search on Native).
 */
internal expect class SortedBytesStore() {
    val size: Int

    fun get(key: ByteArray): ByteArray?
    fun put(key: ByteArray, value: ByteArray)
    fun remove(key: ByteArray)

    /**
     * A point-in-time copy of the store.
     */
    fun snapshotCopy(): SortedBytesStore

    /**
     * Returns ordered copies of keys.
     */
    fun keysOrdered(): List<ByteArray>

    /**
     * Returns ordered copies of entries.
     */
    fun entriesOrdered(): List<Pair<ByteArray, ByteArray>>

    /**
     * First key >= [key], or null.
     */
    fun ceilingKey(key: ByteArray): ByteArray?

    /**
     * Last key <= [key], or null.
     */
    fun floorKey(key: ByteArray): ByteArray?

    fun firstKeyOrNull(): ByteArray?
    fun lastKeyOrNull(): ByteArray?
}
