/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb.config

/**
 * Driver-level configuration that feeds native LevelDB
 */
data class LevelDBDriverConfig(
    val createIfMissing: Boolean = true,
    val paranoidChecks: Boolean = false,
    val cacheSize: Int = 0,
    val blockSize: Int = 0,
    val writeBufferSize: Int = 0,
) {
    companion object {
        /** Creates a fresh builder with default driver settings. */
        fun builder(): Builder = Builder()

        /** DSL helper that configures a builder via [block] and returns the result. */
        fun builder(block: Builder.() -> Unit): LevelDBDriverConfig =
            Builder().apply(block).build()
    }

    fun toBuilder(): Builder = Builder(this)

    /** Builder that exposes the underlying LevelDB options before freezing them. */
    class Builder internal constructor(
        createIfMissing: Boolean,
        paranoidChecks: Boolean,
        cacheSize: Int,
        blockSize: Int,
        writeBufferSize: Int,
    ) {
        constructor() : this(
            createIfMissing = true,
            paranoidChecks = false,
            cacheSize = 0,
            blockSize = 0,
            writeBufferSize = 0
        )

        constructor(config: LevelDBDriverConfig) : this(
            createIfMissing = config.createIfMissing,
            paranoidChecks = config.paranoidChecks,
            cacheSize = config.cacheSize,
            blockSize = config.blockSize,
            writeBufferSize = config.writeBufferSize,
        )

        var createIfMissing: Boolean = createIfMissing
            private set
        var paranoidChecks: Boolean = paranoidChecks
            private set
        var cacheSize: Int = cacheSize
            private set
        var blockSize: Int = blockSize
            private set
        var writeBufferSize: Int = writeBufferSize
            private set

        /**
         * Toggle LevelDB’s `createIfMissing` option (see the LevelDB `Options` docs).
         */
        fun createIfMissing(value: Boolean) = apply { createIfMissing = value }

        /**
         * Toggle the native `paranoidChecks` option described in the same [Options](https://github.com/google/leveldb/blob/main/doc/options.md) doc.
         */
        fun paranoidChecks(value: Boolean) = apply { paranoidChecks = value }

        /**
         * Configure how much memory the block cache can use before LevelDB starts evicting entries.
         *
         * A larger value improves read performance for random reads at the cost of more heap usage.
         * See LevelDB’s [Options](https://github.com/google/leveldb/blob/main/doc/options.md#options) doc for
         * guidance on matching your workload to the cache size.
         */
        fun cacheSize(value: Int) = apply { cacheSize = value }

        /**
         * Set the size of individual blocks read from or written to disk.
         *
         * Bigger blocks batch more data per I/O and reduce metadata overhead, while smaller blocks favor low-latency
         * random reads. Refer to the same LevelDB [Options](https://github.com/google/leveldb/blob/main/doc/options.md#options)
         * section for heuristics.
         */
        fun blockSize(value: Int) = apply { blockSize = value }

        /**
         * Adjust the size of the in-memory write buffer that flushes to a new level file when full.
         *
         * Larger buffers delay flushes and lower write amplification, but they also increase recovery time after crashes.
         * The LevelDB [Options](https://github.com/google/leveldb/blob/main/doc/options.md#options) page explains the trade-offs.
         */
        fun writeBufferSize(value: Int) = apply { writeBufferSize = value }

        /**
         * Returns the immutable [LevelDBDriverConfig] described by this builder.
         */
        fun build(): LevelDBDriverConfig = LevelDBDriverConfig(
            createIfMissing = createIfMissing,
            paranoidChecks = paranoidChecks,
            cacheSize = cacheSize,
            blockSize = blockSize,
            writeBufferSize = writeBufferSize,
        )
    }
}
