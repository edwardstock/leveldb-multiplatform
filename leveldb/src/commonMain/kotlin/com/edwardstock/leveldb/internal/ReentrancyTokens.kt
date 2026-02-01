package com.edwardstock.leveldb.internal

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context key for reentry tracking in LevelDBInstance
 */
internal data object ReentryKey : CoroutineContext.Key<ReentryToken>

/**
 * Reentry token stored in coroutine context for nested access tracking
 */
internal class ReentryToken : AbstractCoroutineContextElement(ReentryKey)

internal object ExclusivePathsKey : CoroutineContext.Key<ExclusivePathsToken>
internal class ExclusivePathsToken(
    private val paths: Set<String>,
) : AbstractCoroutineContextElement(ExclusivePathsKey) {

    fun contains(pathKey: String): Boolean = pathKey in paths
    fun plus(pathKey: String): ExclusivePathsToken = ExclusivePathsToken(paths + pathKey)
}

internal object ExclusiveOpsKey : CoroutineContext.Key<ExclusiveOpsToken>
internal class ExclusiveOpsToken(val paths: Set<String>) : AbstractCoroutineContextElement(ExclusiveOpsKey) {
    fun contains(pathKey: String) = pathKey in paths
    fun plus(pathKey: String) = ExclusiveOpsToken(paths + pathKey)
}
