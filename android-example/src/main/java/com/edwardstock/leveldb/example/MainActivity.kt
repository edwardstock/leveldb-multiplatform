@file:OptIn(ExperimentalMaterial3Api::class)

package com.edwardstock.leveldb.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val viewModel: MainViewModel = hiltViewModel()
                val state by viewModel.state.collectAsState()

                Content(
                    state = state,
                    onAddItem = viewModel::addItem,
                    onRemoveItem = viewModel::removeItem
                )


            }
        }
    }

    @Composable
    private fun Content(
        state: MainUiState,
        onAddItem: (String) -> Unit,
        onRemoveItem: (TextItem) -> Unit,
    ) {
        var newItemText by remember { mutableStateOf("") }

        Column {
            TopAppBar(
                title = { Text("LevelDB") },
            )

            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = newItemText,
                label = { Text("Type something") },
                onValueChange = {
                    newItemText = it
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            onAddItem(newItemText)
                            newItemText = ""
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null
                        )
                    }
                }
            )

            if (state.items.isEmpty()) {
                ListItem(
                    headlineContent = { Text("Empty") },
                )
            }

            LazyColumn {
                items(state.items) { item ->
                    ListItem(
                        modifier = Modifier.clickable(
                            enabled = false,
                            onClick = {

                            },
                        ),
                        headlineContent = { Text(item.text) },
                        supportingContent = { Text(item.id.toString()) },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    onRemoveItem(item)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                }
            }

        }
    }

    @Composable
    @PreviewLightDark
    private fun ContentPreview() {
        MaterialTheme {
            Content(
                state = MainUiState(
                    items = List(3) {
                        TextItem("Hello #$it", it)
                    }
                ),
                onAddItem = {},
                onRemoveItem = {}
            )
        }
    }
}
