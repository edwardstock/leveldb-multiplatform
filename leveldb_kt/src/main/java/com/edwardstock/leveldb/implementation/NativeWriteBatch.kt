package com.edwardstock.leveldb.implementation

import com.edwardstock.leveldb.LevelDB.Companion.loadNative
import com.edwardstock.leveldb.WriteBatch
import java.io.Closeable

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
 * Native object a-la <tt>leveldb::WriteBatch</tt>.
 *
 * Make sure after use you call [NativeWriteBatch.close].
 */
open class NativeWriteBatch(writeBatch: WriteBatch) : Closeable {

    // Don't touch this. If you do, something somewhere dies.
    private var nwb: Long

    init {
        nwb = ncreate()
        for (operation in writeBatch) {
            if (operation.isPut) {
                nput(nwb, operation.key(), operation.value())
            } else {
                ndelete(nwb, operation.key())
            }
        }
    }

    companion object {
        init {
            loadNative()
        }

        /**
         * Native create. Corresponds to: <tt>new leveldb::SimpleWriteBatch()</tt>
         *
         * @return pointer to nat structure
         */
        private external fun ncreate(): Long

        /**
         * Native SimpleWriteBatch put. Pointer is unchecked.
         *
         * @param nwb   nat structure pointer
         * @param key
         * @param value
         */
        private external fun nput(nwb: Long, key: ByteArray, value: ByteArray?)

        /**
         * Native SimpleWriteBatch delete. Pointer is unchecked.
         *
         * @param nwb nat structure pointer
         * @param key
         */
        private external fun ndelete(nwb: Long, key: ByteArray)

        /**
         * Native close. Releases all memory. Pointer is unchecked.
         *
         * @param nwb nat structure pointer
         */
        private external fun nclose(nwb: Long)


    }


    /**
     * Returns the nat object's pointer, to be used when calling a nat function.
     *
     * @return the nat pointer
     */
    internal fun nativePointer(): Long {
        return nwb
    }

    /**
     * Close this object. You may call this multiple times.
     *
     * Use of this object is illegal after calling this.
     */
    override fun close() {
        if (!isClosed) {
            nclose(nwb)
            nwb = 0
        } else {
//            Log.i("org.leveldb", "Native WriteBatch is already closed.")
        }
    }

    /**
     * Whether this object is closed.
     *
     * @return
     */
    val isClosed: Boolean
        get() = nwb == 0L


}