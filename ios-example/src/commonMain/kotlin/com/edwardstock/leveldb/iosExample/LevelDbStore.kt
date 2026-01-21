package com.edwardstock.leveldb.iosExample

import com.edwardstock.leveldb.LevelDBConfig
import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.utils.forEachAll
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
    private val db = LevelDBInstance(
        path = defaultDbPath(),
        config = LevelDBConfig(createIfMissing = true),
    )

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
                write {
                    put(id.toString(), trimmed)
                }
            }
            _items.value = _items.value + TextItem(id, trimmed)
        }
    }

    fun removeItem(item: TextItem) {
        scope.launch {
            db.use {
                write {
                    del(item.id.toString())
                }
            }
            _items.value = _items.value.filterNot { it.id == item.id }
        }
    }

    private suspend fun reload() {
        val loaded = mutableListOf<TextItem>()
        db.use {
            read {
                forEachAll { key, value ->
                    val id = key.toLongOrNull() ?: return@forEachAll
                    loaded.add(TextItem(id, value))
                }
            }
        }
        _items.value = loaded.sortedBy { it.id }
    }

    override fun close() {
        scope.cancel()
        db.close()
    }
}
