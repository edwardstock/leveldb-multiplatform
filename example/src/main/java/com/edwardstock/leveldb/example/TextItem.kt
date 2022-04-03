package com.edwardstock.leveldb.example

var TEXT_ID = 0

data class TextItem(
    val text: String,
    val id: Int = TEXT_ID++
)
