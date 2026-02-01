package com.edwardstock.leveldb.internal

import com.edwardstock.leveldb.exception.LevelDBIOException
import okio.FileSystem
import okio.Path
import okio.fakefilesystem.FakeFileSystem

internal fun canonicalize(fs: FileSystem, p: Path): String {
    if (!fs.isFake()) {
        if (!fs.exists(p)) {
            fs.createDirectory(p)
        }

        return fs.canonicalize(p).toString()
    }

    return p.toString()
}

private fun FileSystem.isFake(): Boolean = this is FakeFileSystem

internal fun fsIdentity(fs: FileSystem): Any = fs

internal fun checkSameFileSystem(entry: LevelDBEntryState, fs: FileSystem) {
    if (entry.fsId !== fsIdentity(fs)) {
        throw LevelDBIOException("Mismatched FileSystem for path ${entry.path}. Use the same FileSystem as when entry was created.")
    }
}
