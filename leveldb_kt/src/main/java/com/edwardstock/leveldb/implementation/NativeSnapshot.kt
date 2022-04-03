package com.edwardstock.leveldb.implementation

import com.edwardstock.leveldb.LevelDB
import com.edwardstock.leveldb.Snapshot
import java.lang.ref.WeakReference

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
 */
open class NativeSnapshot(owner: NativeLevelDB, nsnapshot: Long) : Snapshot() {
    private val owner: WeakReference<LevelDB> = WeakReference(owner)

    @Volatile
    private var nsnapshot: Long

    init {
        this.nsnapshot = nsnapshot
    }

    override val isReleased: Boolean
        get() {
            val owner = owner.get()
            return nsnapshot == 0L || owner == null || owner.isClosed
        }

    fun checkOwner(db: LevelDB): Boolean {
        val owner = owner.get()
        return owner === db
    }

    fun release(): Long {
        val snapshot = nsnapshot

        // nsnapshot may change concurrently here, but only to 0 which we're fine with
        nsnapshot = 0
        return snapshot
    }

    fun id(): Long {
        return nsnapshot
    }


}