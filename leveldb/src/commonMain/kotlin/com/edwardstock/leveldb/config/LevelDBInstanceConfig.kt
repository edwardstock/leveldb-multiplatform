package com.edwardstock.leveldb.config

import com.edwardstock.leveldb.AdapterRegistry
import com.edwardstock.leveldb.api.LevelDB
import com.edwardstock.leveldb.api.LevelDBInstanceFactory
import com.edwardstock.leveldb.impl.NativeLevelDB
import com.edwardstock.leveldb.log.LevelDBLogger
import com.edwardstock.leveldb.log.LevelDBNullLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Higher-level configuration attached to a [LevelDBInstance].
 * Contains schema, adapter and lifecycle helpers that do not belong to the native driver config.
 */
data class LevelDBInstanceConfig(
    val logger: LevelDBLogger = LevelDBNullLogger,
    val driver: LevelDBDriverConfig = LevelDBDriverConfig(createIfMissing = true),
    val instanceFactory: LevelDBInstanceFactory = LevelDBInstanceFactory { path, driverCfg -> NativeLevelDB(path, driverCfg) },
    val adapterRegistry: AdapterRegistry = AdapterRegistry(),
) {
    sealed interface CloseStrategy {
        data object Immediate : CloseStrategy
        data class IdleDelayed(val duration: Duration = 30.seconds) : CloseStrategy
    }

    internal val requireLogger: LevelDBLogger
        get() = logger

    companion object {
        /** Creates a mutable builder initialized with default values. */
        fun builder(): Builder = Builder()

        /** DSL entry point that configures a builder via [block] and returns the built config. */
        fun builder(block: Builder.() -> Unit): LevelDBInstanceConfig =
            Builder().apply(block).build()
    }

    /** Builder that configures logger, driver, factory, and adapters before creating a config. */
    class Builder internal constructor(
        logger: LevelDBLogger,
        driver: LevelDBDriverConfig,
        instanceFactory: LevelDBInstanceFactory,
        adapterRegistry: AdapterRegistry,
    ) {
        constructor() : this(
            logger = LevelDBNullLogger,
            instanceFactory = LevelDBInstanceFactory { path, driverCfg -> NativeLevelDB(path, driverCfg) },
            adapterRegistry = AdapterRegistry(),
            driver = LevelDBDriverConfig()
        )

        constructor(config: LevelDBInstanceConfig) : this(
            logger = config.logger,
            driver = config.driver,
            instanceFactory = config.instanceFactory,
            adapterRegistry = config.adapterRegistry,
        )

        private var logger: LevelDBLogger = logger
        private var driverBuilder: LevelDBDriverConfig.Builder = LevelDBDriverConfig.Builder(driver)
        private var instanceFactory: LevelDBInstanceFactory = instanceFactory
        private val adapterRegistry: AdapterRegistry = adapterRegistry.copy()

        /**
         * Override the logger stored inside the config.
         */
        fun logger(logger: LevelDBLogger) = apply { this.logger = logger }

        /**
         * Replace the configured factory that creates native `LevelDB` instances.
         */
        fun dbFactory(factory: LevelDBInstanceFactory) = apply { instanceFactory = factory }

        /**
         * Mutate adapters through a registry receiver; use [AdapterRegistry.addAdapter] inside.
         */
        fun adapters(block: AdapterRegistry.() -> Unit) = apply { adapterRegistry.block() }

        /**
         * Swap the whole [LevelDBDriverConfig] instance.
         */
        fun driver(config: LevelDBDriverConfig) = apply { driverBuilder = LevelDBDriverConfig.Builder(config) }

        /**
         * Tune the driver via its builder DSL
         */
        fun driver(block: LevelDBDriverConfig.Builder.() -> Unit) = apply { driverBuilder.apply(block) }

        /**
         * Returns a config capturing the current builder state.
         */
        fun build(): LevelDBInstanceConfig = LevelDBInstanceConfig(
            logger = logger,
            driver = driverBuilder.build(),
            instanceFactory = instanceFactory,
            adapterRegistry = adapterRegistry.copy(),
        )
    }
}

fun LevelDBInstanceConfig.toBuilder(): LevelDBInstanceConfig.Builder =
    LevelDBInstanceConfig.Builder(this)

internal fun LevelDBInstanceConfig.createDB(path: String): LevelDB {
    return instanceFactory(path, this)
}
