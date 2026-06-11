package com.edwardstock.leveldb.util

actual fun stressConfig(): StressConfig = StressConfig(
    workers = 10,
    iterations = 50,
    exclusiveRepeats = 5,
)
