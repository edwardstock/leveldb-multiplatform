@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package com.edwardstock.leveldb.util

actual fun stressConfig(): StressConfig {
    return when (Platform.osFamily) {
        OsFamily.MACOSX, OsFamily.IOS -> StressConfig(workers = 4, iterations = 20, exclusiveRepeats = 2)
        else -> StressConfig(workers = 6, iterations = 30, exclusiveRepeats = 3)
    }
}
