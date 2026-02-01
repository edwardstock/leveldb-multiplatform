package com.edwardstock.leveldb.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edwardstock.leveldb.LevelDBInstance
import com.edwardstock.leveldb.api.del
import com.edwardstock.leveldb.api.forEachAllValueT
import com.edwardstock.leveldb.api.putValue
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
                val data = ArrayList<TextItem>()
                forEachAllValueT<TextItem> { value ->
                    data.add(value)
                }
                _state.update {
                    it.copy(
                        items = data
                    )
                }
            }
        }
    }

    fun addItem(text: String) {
        viewModelScope.launch {
            val id = System.currentTimeMillis().toInt()
            val item = TextItem(text, id)
            db.use {
                putValue(id.toString(), item, TextItem::class)
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
                del(item.id.toString())
            }
            _state.update { oldState ->
                oldState.copy(
                    items = oldState.items.filterNot { it.id == item.id }
                )
            }
        }
    }
}
