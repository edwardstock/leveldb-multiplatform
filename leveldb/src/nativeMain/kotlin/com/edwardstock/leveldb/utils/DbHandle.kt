/*
 * Copyright (c) 2026 Eduard Maximovich
 * Licensed under the MIT License. See the LICENSE file in the project root
 */

@file:OptIn(ExperimentalForeignApi::class, ExperimentalForeignApi::class)

package com.edwardstock.leveldb.utils

import cnames.structs.ndb_batch_holder
import cnames.structs.ndb_holder
import cnames.structs.ndb_iterator_holder
import cnames.structs.ndb_snapshot_holder
import com.edwardstock.leveldb.internal.DbHandle
import com.edwardstock.leveldb.interop.leveldb_batch_close
import com.edwardstock.leveldb.interop.leveldb_close
import com.edwardstock.leveldb.interop.leveldb_iter_close
import com.edwardstock.leveldb.interop.leveldb_release_snapshot
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

class NativeDbHandle internal constructor(
    ptr: CPointer<ndb_holder>?,
) : NativeHandle<ndb_holder>(ptr) {

    override fun closeInternal() {
        ptr?.let {
            leveldb_close(ptr)
            ptr = null
        }
    }
}

class NativeDbBatchHandle internal constructor(
    ptr: CPointer<ndb_batch_holder>?,
) : NativeHandle<ndb_batch_holder>(ptr) {

    override fun closeInternal() {
        ptr?.let {
            leveldb_batch_close(ptr)
            ptr = null
        }
    }
}

class NativeDbSnapshotHandle internal constructor(
    private val dbHandle: DbHandle,
    ptr: CPointer<ndb_snapshot_holder>?,
) : NativeHandle<ndb_snapshot_holder>(ptr) {

    override fun closeInternal() {
        dbHandle.ptr?.let { dbPtr ->
            ptr?.let {
                leveldb_release_snapshot(holder = dbPtr, snapshot_holder = ptr)
                ptr = null
            }
        }
    }
}

class NativeDbIteratorHandle internal constructor(
    ptr: CPointer<ndb_iterator_holder>?,
) : NativeHandle<ndb_iterator_holder>(ptr) {

    override fun closeInternal() {
        ptr?.let {
            leveldb_iter_close(ptr)
            ptr = null
        }
    }
}
