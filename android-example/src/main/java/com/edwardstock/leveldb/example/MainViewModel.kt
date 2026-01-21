package com.edwardstock.leveldb.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.utils.forEachAll
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val items: List<TextItem>,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val db: LevelDBInstance,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState(emptyList()))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            db.use {
                read {
                    val data = ArrayList<TextItem>()
                    forEachAll { key, value ->
                        val item = TextItem(value, key.toInt())
                        data.add(item)
                    }
                    _state.update {
                        it.copy(
                            items = data
                        )
                    }
                }
            }
        }
    }

    fun addItem(text: String) {
        viewModelScope.launch {
            val id = System.currentTimeMillis().toInt()
            val item = TextItem(text, id)
            db.use {
                write {
                    put(id.toString(), text)
                }
            }
            _state.update {
                it.copy(
                    items = it.items + item
                )
            }
        }
    }

    fun removeItem(item: TextItem) {
        viewModelScope.launch {
            db.use {
                write {
                    del(item.id.toString())
                }
            }
            _state.update { oldState ->
                oldState.copy(
                    items = oldState.items.filterNot { it.id == item.id }
                )
            }
        }
    }
}
