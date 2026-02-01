package com.edwardstock.leveldb.internal

import com.edwardstock.leveldb.config.LevelDBInstanceConfig
import com.edwardstock.leveldb.config.LevelDBInstanceConfig.CloseStrategy.IdleDelayed
import com.edwardstock.leveldb.config.LevelDBInstanceConfig.CloseStrategy.Immediate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal val LevelDBInstanceConfig.CloseStrategy.shouldCloseImmediately: Boolean
    get() = when {
        this is Immediate -> true
        this is IdleDelayed && duration.isNegative() -> true
        else -> false
    }

internal val LevelDBInstanceConfig.CloseStrategy.duration: Duration
    get() = when (this) {
        is Immediate -> (-1).seconds
        is IdleDelayed -> duration
    }
