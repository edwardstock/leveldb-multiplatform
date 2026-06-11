package com.edwardstock.leveldb.internal

import kotlin.jvm.JvmStatic

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
 */

/**
 * Utility functions for working with byte arrays
 */
internal object BytesHelper {
    /**
     * Utility [java.util.Comparator] for lexicographic comparisons of byte arrays
     *
     * @see .lexicographicCompare
     */
    val COMPARATOR: Comparator<ByteArray> = Comparator { a, b -> lexicographicCompare(a, b) }

    fun ByteArray?.lexicographicCompareTo(other: ByteArray?): Int {
        return lexicographicCompare(this, other)
    }

    /**
     * Lexicographically compares two byte arrays using the same byte-wise ordering as LevelDB's
     * default comparator (`leveldb::BytewiseComparator`): bytes are compared as unsigned over the
     * common prefix, and if one array is a prefix of the other, the shorter one sorts first.
     *
     * This matches the on-disk key order of [com.edwardstock.leveldb.impl.NativeLevelDB], so the
     * in-memory test doubles order keys exactly like the native database.
     *
     * @param a nullable byte array
     * @param b nullable byte array
     * @return greater than 0 if a > b, less than 0 if a < b, or 0 if a = b
     */
    @JvmStatic
    fun lexicographicCompare(a: ByteArray?, b: ByteArray?): Int {
        if (a === b) return 0
        if (a == null) return -1
        if (b == null) return 1

        val minLength = a.size.coerceAtMost(b.size)
        for (i in 0 until minLength) {
            val ca = a[i].toInt() and 0xFF
            val cb = b[i].toInt() and 0xFF
            if (ca != cb) {
                return ca - cb
            }
        }
        // Common prefix is equal: the shorter array sorts first.
        return a.size - b.size
    }
}
