/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

package com.edwardstock.leveldb

import com.edwardstock.leveldb.utils.BytesHelper

fun assertEquals(expected: ByteArray?, actual: ByteArray?): Boolean {
    return when {
        expected == null && actual == null -> true
        expected == null || actual == null -> false
        expected.size != actual.size -> false
        else -> BytesHelper.lexicographicCompare(expected, actual) == 0
    }
}
