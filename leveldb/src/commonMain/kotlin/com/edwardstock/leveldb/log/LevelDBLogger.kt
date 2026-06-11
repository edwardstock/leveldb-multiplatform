package com.edwardstock.leveldb.log

import com.edwardstock.leveldb.internal.currentThreadName
import com.edwardstock.leveldb.internal.stderrPrintln
import com.edwardstock.leveldb.internal.stdoutPrintln

/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

interface LevelDBLogger {
    fun logV(message: String)
    fun logV(e: Throwable, message: String? = null)

    fun logD(message: String)
    fun logD(e: Throwable, message: String? = null)

    fun logI(message: String)
    fun logI(e: Throwable, message: String? = null)

    fun logW(message: String)
    fun logW(e: Throwable, message: String? = null)

    fun logE(message: String)
    fun logE(e: Throwable, message: String? = null)
}

data object LevelDBNullLogger : LevelDBLogger {
    override fun logV(message: String) = Unit
    override fun logV(e: Throwable, message: String?) = Unit
    override fun logD(message: String) = Unit
    override fun logD(e: Throwable, message: String?) = Unit
    override fun logI(message: String) = Unit
    override fun logI(e: Throwable, message: String?) = Unit
    override fun logW(message: String) = Unit
    override fun logW(e: Throwable, message: String?) = Unit
    override fun logE(message: String) = Unit
    override fun logE(e: Throwable, message: String?) = Unit
}

data class LevelDBConsoleLogger(
    val printThread: Boolean = false,
) : LevelDBLogger {

    override fun logV(message: String) = log("V", message, null, isError = false)
    override fun logV(e: Throwable, message: String?) = log("V", message, e, isError = false)

    override fun logD(message: String) = log("D", message, null, isError = false)
    override fun logD(e: Throwable, message: String?) = log("D", message, e, isError = false)

    override fun logI(message: String) = log("I", message, null, isError = false)
    override fun logI(e: Throwable, message: String?) = log("I", message, e, isError = false)

    override fun logW(message: String) = log("W", message, null, isError = false)
    override fun logW(e: Throwable, message: String?) = log("W", message, e, isError = false)

    override fun logE(message: String) = log("E", message, null, isError = true)
    override fun logE(e: Throwable, message: String?) = log("E", message, e, isError = true)

    private fun log(level: String, message: String?, e: Throwable?, isError: Boolean) {
        val text = buildString {
            if (printThread) {
                append("T=")
                append(currentThreadName())
                append(" ")
            }
            append(level)
            append(": ")

            val msg = message?.takeIf { it.isNotBlank() }
            if (msg != null) {
                append(msg)
            }

            if (e != null) {
                if (msg != null) append('\n')
                append(e.stackTraceToString())
            }
        }

        if (isError) {
            stderrPrintln(text)
        } else {
            stdoutPrintln(text)
        }
    }
}
