package com.edwardstock.leveldb.iosExample

import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal actual fun appDataPath(): Path {
    val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    val dir = paths.firstOrNull() as? String
        ?: error("Failed to resolve documents directory")
    return dir.toPath()
}
