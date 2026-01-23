package com.edwardstock.leveldb.util

data class StressConfig(
    val workers: Int,
    val iterations: Int,
    val exclusiveRepeats: Int,
)

expect fun stressConfig(): StressConfig
