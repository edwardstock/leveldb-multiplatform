package com.edwardstock.leveldb.iosExample

import okio.Path

internal expect fun appDataPath(): Path

internal fun defaultDbPath(): Path = appDataPath().resolve("leveldb")
