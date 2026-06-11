package com.edwardstock.leveldb.iosExample

import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.del
import com.edwardstock.leveldb.api.forEachAll
import com.edwardstock.leveldb.api.putString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class LevelDbStore : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = LevelDBInstance.builder(path = defaultDbPath())
        .build()

    private val _items = MutableStateFlow<List<TextItem>>(emptyList())
    val items: StateFlow<List<TextItem>> = _items.asStateFlow()

    init {
        scope.launch {
            reload()
        }
    }

    fun addItem(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        scope.launch {
            val id = nowMillis()
            db.use {
                putString(id.toString(), trimmed)
            }
            _items.value += TextItem(id, trimmed)
        }
    }

    fun removeItem(item: TextItem) {
        scope.launch {
            db.use {
                del(item.id.toString())
            }
            _items.value = _items.value.filterNot { it.id == item.id }
        }
    }

    private suspend fun reload() {
        val loaded = mutableListOf<TextItem>()
        db.use {
            forEachAll { entry ->
                val id = entry.keyString().toLongOrNull() ?: return@forEachAll
                loaded.add(TextItem(id, entry.valueString()))
            }
        }
        _items.value = loaded.sortedBy { it.id }
    }

    override fun close() {
        scope.cancel()
        db.close()
    }
}
