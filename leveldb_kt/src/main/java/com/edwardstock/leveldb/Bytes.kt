package com.edwardstock.leveldb

/*
 * Stojan Dimitrovski
 *
 * Copyright (c) 2014, Stojan Dimitrovski <sdimitrovski@gmail.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OFz SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */ /**
 * Utility functions for working with byte arrays.
 */
object Bytes {
    /**
     * Utility [java.util.Comparator] for lexicographic comparisons of byte arrays.
     *
     * @see .lexicographicCompare
     */
    val COMPARATOR: Comparator<ByteArray> = Comparator { a, b -> lexicographicCompare(a, b) }

    /**
     * Lexicographically compares two byte arrays, the way the default comparator in a
     * [com.edwardstock.leveldb.implementation.NativeLevelDB] instance works.
     *
     * @param a nullable byte array
     * @param b nullable byte array
     * @return greater than 0 if a > b, less than 0 if a < b, or 0 if a = b
     */
    @JvmStatic
    fun lexicographicCompare(a: ByteArray?, b: ByteArray?): Int {
        if (a.contentEquals(b)) {
            return 0
        }
        if (a == null) {
            return -1
        }
        if (b == null) {
            return 1
        }

        val maxLength = a.size.coerceAtMost(b.size)
        for (i in 0 until maxLength) {
            if (a[i].toLong() and 0xFF == b[i].toLong() and 0xFF) {
                continue
            }
            return if (a[i].toLong() and 0xFF > b[i].toLong() and 0xFF) {
                i + 1
            } else -(i + 1)
        }
        if (a.size > maxLength) {
            for (i in b.size until a.size) {
                if (a[i] != 0.toByte()) {
                    return i + 1
                }
            }
        }
        for (i in a.size until b.size) {
            if (b[i] != 0.toByte()) {
                return -(i + 1)
            }
        }
        return 0
    }
}