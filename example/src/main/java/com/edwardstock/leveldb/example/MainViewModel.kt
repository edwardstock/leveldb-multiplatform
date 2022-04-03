package com.edwardstock.leveldb.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edwardstock.leveldb.implementation.LevelDBInstance
import com.edwardstock.leveldb.implementation.forEachAll
import com.edwardstock.leveldb.implementation.leveldbContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val db: LevelDBInstance
) : ViewModel() {

    val addItemFlow = MutableSharedFlow<String?>()
    val adapter = RowsAdapter(this::onItemDelete)

    init {
        viewModelScope.launch {
            addItemFlow
                .filterNotNull()
                .filterNot { it.isEmpty() }
                .map { TextItem(it) }
                .collect {
                    leveldbContext(db) {
                        put(it.id.toString(), it.text)
                    }

                    adapter.addItem(it)
                }
        }

        val data = ArrayList<TextItem>()

        leveldbContext(db) {
            forEachAll { key, value ->
                val item = TextItem(value, key.toInt())
                data.add(item)
            }
            adapter.setData(data)
        }

    }

    private fun onItemDelete(item: TextItem) {
        leveldbContext(db) {
            del(item.id.toString())
        }
        adapter.removeItem(item)
    }

}
