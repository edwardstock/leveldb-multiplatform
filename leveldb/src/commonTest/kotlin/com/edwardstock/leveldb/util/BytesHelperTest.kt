package com.edwardstock.leveldb.util

import com.edwardstock.leveldb.internal.BytesHelper.lexicographicCompare
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Original author: Stojan Dimitrovski <sdimitrovski@gmail.com>
 * Modified by: Eduard Maximovich <edward.vstock@gmail.com>
 *
 * (Original BSD 3-Clause License follows)
 *
 * Copyright (c) 2014, Stojan Dimitrovski <sdimitrovski@gmail.com>
 *
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 */ class BytesHelperTest {
    @Throws(Exception::class)
    @Test
    fun testLexicographicComparison() {
        val a = byteArrayOf(1, 2, 3, 0, 0, 0)
        val b = byteArrayOf(0xFF.toByte(), 0, 0) // { 255 }
        assertTrue(lexicographicCompare(a, b) < 0)
        assertTrue(lexicographicCompare(b, a) > 0)

        // A prefix sorts before the longer key that extends it (leveldb::BytewiseComparator),
        // even when the extra bytes are zero.
        val prefix = byteArrayOf(1, 2, 3)
        val extended = byteArrayOf(1, 2, 3, 0, 0, 0)
        assertTrue(lexicographicCompare(prefix, extended) < 0)
        assertTrue(lexicographicCompare(extended, prefix) > 0)

        // Empty array sorts before any non-empty array.
        assertTrue(lexicographicCompare(byteArrayOf(), byteArrayOf(0)) < 0)

        // Bytes compare as unsigned: 0x80 (128) > 0x7F (127).
        assertTrue(lexicographicCompare(byteArrayOf(0x80.toByte()), byteArrayOf(0x7F)) > 0)

        // Identical content compares equal.
        assertEquals(0, lexicographicCompare(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
    }
}
