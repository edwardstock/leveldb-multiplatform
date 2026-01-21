#include "LevelDBNativeProvider.h"

#include "JniHelpers.h"
#include "../cinterop/cleveldb.h"
#include "leveldb/cache.h"
#include "leveldb/db.h"
#include "leveldb/env.h"

#ifdef __cplusplus
extern "C" {
#endif

using namespace leveldb_jni;

// Throws the appropriate Java exception for the given status. Make sure you
// check IsNotFound() and similar possible non-exception statuses before calling
// this. Please release all Java references before calling this.
void checkStatusOrThrow(JNIEnv* env, const cleveldb_status& status, const char* msg = nullptr) {
    if (status == LDB_OK) {
        return;
    }

    if (status == LDB_IO_ERROR) {
        jclass ioExceptionClass = env->FindClass("com/edwardstock/leveldb/exception/LevelDBIOException");

        env->ThrowNew(ioExceptionClass, msg ? msg : "IO Error occurred");
    } else if (status == LDB_CORRUPTED_DATA) {
        jclass corruptionExceptionClass = env->FindClass("com/edwardstock/leveldb/exception/LevelDBCorruptionException");

        env->ThrowNew(corruptionExceptionClass, msg ? msg : "Corrupted data encountered");
    } else if (status == LDB_NOT_FOUND) {
        jclass notFoundExceptionClass = env->FindClass("com/edwardstock/leveldb/exception/LevelDBNotFoundException");

        env->ThrowNew(notFoundExceptionClass, msg ? msg : "Requested key not found");
    } else {
        jclass exceptionClass = env->FindClass("com/edwardstock/leveldb/exception/LevelDBException");

        const char* message;
        if (status == LDB_UNKNOWN_ERROR) {
            message = "Unknown error";
        } else if (status == LDB_NOT_SUPPORTED) {
            message = "Operation not supported";
        } else if (status == LDB_INVALID_ARGUMENT) {
            message = "Invalid argument";
        } else if (status == LDB_MEMORY_ERROR) {
            message = "Memory allocation error";
        } else if (status == LDB_INVALID_POINTER) {
            message = "Invalid pointer";
        } else {
            message = "Unrecognized error";
        }

        env->ThrowNew(exceptionClass, msg ? msg : message);
    }
}

static bool checkHandleOrThrow(jlong handle, JNIEnv* env) {
    if (handle == 0L) {
        checkStatusOrThrow(env, LDB_INVALID_POINTER);
        return false;
    }

    return true;
}

jlong Java_com_edwardstock_leveldb_LevelDBNativeProvider_dbOpen(
    JNIEnv* env,
    jobject,
    jboolean createIfMissing,
    jboolean paranoid_checks,
    jint cacheSize,
    jint blockSize,
    jint writeBufferSize,
    jstring path
) {
    ScopedUtfChars nativePath(env, path);
    if (!nativePath.isValid()) {
        checkStatusOrThrow(env, LDB_MEMORY_ERROR, "Failed to get native path");
        return 0L;
    }

    ndb_holder* handle = nullptr;

    auto rc = leveldb_open(
        createIfMissing == JNI_TRUE,
        paranoid_checks == JNI_TRUE,
        static_cast<size_t>(cacheSize),
        static_cast<size_t>(blockSize),
        static_cast<size_t>(writeBufferSize),
        nativePath.c_str(),
        &handle
    );

    checkStatusOrThrow(env, rc);

    return reinterpret_cast<jlong>(handle);
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_dbDestroy(JNIEnv* env, jobject, jstring path) {
    ScopedUtfChars nativePath(env, path);
    if (!nativePath.isValid()) {
        checkStatusOrThrow(env, LDB_MEMORY_ERROR, "Failed to get native path");
        return;
    }

    checkStatusOrThrow(env, leveldb_destroy(nativePath));
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_dbRepair(JNIEnv* env, jobject, jstring path) {
    ScopedUtfChars nativePath(env, path);
    if (!nativePath.isValid()) {
        checkStatusOrThrow(env, LDB_MEMORY_ERROR, "Failed to get native path");
        return;
    }

    checkStatusOrThrow(env, leveldb_repair(nativePath));
}

jlong Java_com_edwardstock_leveldb_LevelDBNativeProvider_batchCreate(JNIEnv*, jobject) {
    ndb_batch_holder* batch = leveldb_batch_create();
    return reinterpret_cast<jlong>(batch);
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_batchClose(JNIEnv*, jobject, jlong holder) {
    if (holder == 0L) {
        return;
    }
    auto batch = reinterpret_cast<ndb_batch_holder *>(holder);
    leveldb_batch_close(batch);
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_batchDelete(JNIEnv* env, jobject, jlong handle, jbyteArray key) {
    ScopedByteArrayElements keyData(env, key);
    if (!keyData.isValid()) {
        checkStatusOrThrow(env, LDB_MEMORY_ERROR, "Failed to get key data");
        return;
    }
    auto batch = reinterpret_cast<ndb_batch_holder *>(handle);

    leveldb_batch_delete(batch, keyData.bytes(), keyData.usize());
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_batchPut(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray key,
    jbyteArray value
) {
    ScopedByteArrayElements keyData(env, key);
    if (!keyData.isValid()) {
        checkStatusOrThrow(env, LDB_MEMORY_ERROR, "Failed to get key data");
        return;
    }
    ScopedByteArrayElements valueData(env, value);
    if (!valueData.isValid()) {
        checkStatusOrThrow(env, LDB_MEMORY_ERROR, "Failed to get value data");
        return;
    }
    auto batch = reinterpret_cast<ndb_batch_holder *>(handle);

    leveldb_batch_put(
        batch,
        keyData.bytes(),
        keyData.usize(),
        valueData.bytes(),
        valueData.usize()
    );
}

jbyteArray Java_com_edwardstock_leveldb_LevelDBNativeProvider_iteratorValue(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    if (!checkHandleOrThrow(handle, env)) {
        return nullptr;
    }
    auto iterator = reinterpret_cast<ndb_iterator_holder *>(handle);

    uint8_t* buffer = nullptr;
    size_t buffer_len = 0;

    const cleveldb_status rc = leveldb_iter_value(iterator, &buffer, &buffer_len);
    checkStatusOrThrow(env, rc);

    if (buffer_len == 0) {
        return NewByteArrayFromBytes(env, nullptr, 0);
    }

    auto result = NewByteArrayFromBytes(env, buffer, buffer_len);

    leveldb_free_buffer(buffer);

    return result;
}

jbyteArray Java_com_edwardstock_leveldb_LevelDBNativeProvider_iteratorKey(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    if (!checkHandleOrThrow(handle, env)) {
        return nullptr;
    }
    auto iterator = reinterpret_cast<ndb_iterator_holder *>(handle);

    uint8_t* buffer = nullptr;
    size_t buffer_len = 0;

    const cleveldb_status rc = leveldb_iter_key(iterator, &buffer, &buffer_len);
    checkStatusOrThrow(env, rc);

    if (buffer_len == 0) {
        return NewByteArrayFromBytes(env, nullptr, 0);
    }

    auto result = NewByteArrayFromBytes(env, buffer, buffer_len);

    leveldb_free_buffer(buffer);

    return result;
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_iteratorPrev(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    if (!checkHandleOrThrow(handle, env)) {
        return;
    }
    auto iterator = reinterpret_cast<ndb_iterator_holder *>(handle);

    leveldb_iter_prev(iterator);
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_iteratorNext(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    if (!checkHandleOrThrow(handle, env)) {
        return;
    }
    auto iterator = reinterpret_cast<ndb_iterator_holder *>(handle);

    leveldb_iter_next(iterator);
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_iteratorSeekToLast(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    if (!checkHandleOrThrow(handle, env)) {
        return;
    }
    auto iterator = reinterpret_cast<ndb_iterator_holder *>(handle);

    leveldb_iter_seek_to_last(iterator);
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_iteratorSeekToFirst(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    if (!checkHandleOrThrow(handle, env)) {
        return;
    }
    auto iterator = reinterpret_cast<ndb_iterator_holder *>(handle);

    leveldb_iter_seek_to_first(iterator);
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_iteratorSeek(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray key
) {
    if (!checkHandleOrThrow(handle, env)) {
        return;
    }
    ScopedByteArrayElements keyData(env, key);
    if (!keyData.isValid()) {
        checkStatusOrThrow(env, LDB_MEMORY_ERROR, "Failed to get key data");
        return;
    }
    auto iterator = reinterpret_cast<ndb_iterator_holder *>(handle);

    leveldb_iter_seek(iterator, keyData.bytes(), keyData.usize());
}

jboolean Java_com_edwardstock_leveldb_LevelDBNativeProvider_iteratorValidate(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    if (!checkHandleOrThrow(handle, env)) {
        return JNI_FALSE;
    }
    auto iterator = reinterpret_cast<ndb_iterator_holder *>(handle);

    return leveldb_iter_valid(iterator) ? JNI_TRUE : JNI_FALSE;
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_iteratorClose(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    if (handle == 0L) {
        return;
    }
    auto iterator = reinterpret_cast<ndb_iterator_holder *>(handle);

    leveldb_iter_close(iterator);
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_dbReleaseSnapshot(
    JNIEnv* env,
    jobject,
    jlong dbHandle,
    jlong snapshotHandle
) {
    if (!checkHandleOrThrow(dbHandle, env)) {
        return;
    }
    if (!checkHandleOrThrow(snapshotHandle, env)) {
        return;
    }
    auto db = reinterpret_cast<ndb_holder *>(dbHandle);
    auto snapshot = reinterpret_cast<ndb_snapshot_holder *>(snapshotHandle);

    leveldb_release_snapshot(db, snapshot);
}

jlong Java_com_edwardstock_leveldb_LevelDBNativeProvider_dbSnapshot(
    JNIEnv* env,
    jobject,
    jlong dbHandle
) {
    if (!checkHandleOrThrow(dbHandle, env)) {
        return 0L;
    }
    auto db = reinterpret_cast<ndb_holder *>(dbHandle);

    ndb_snapshot_holder* snapshot = leveldb_snapshot(db);

    return reinterpret_cast<jlong>(snapshot);
}

jlong Java_com_edwardstock_leveldb_LevelDBNativeProvider_dbIterate(
    JNIEnv* env,
    jobject,
    jlong dbHandle,
    jboolean fillCache,
    jlong snapshotHandle
) {
    if (!checkHandleOrThrow(dbHandle, env)) {
        return 0L;
    }
    auto db = reinterpret_cast<ndb_holder *>(dbHandle);
    auto* snapshot = reinterpret_cast<ndb_snapshot_holder *>(snapshotHandle);

    ndb_iterator_holder* iterator = leveldb_iterate(db, fillCache == JNI_TRUE, snapshot);

    return reinterpret_cast<jlong>(iterator);
}

jbyteArray Java_com_edwardstock_leveldb_LevelDBNativeProvider_dbGetProperty(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray key
) {
    if (!checkHandleOrThrow(handle, env)) {
        return nullptr;
    }
    ScopedByteArrayElements keyData(env, key);
    if (!keyData.isValid()) {
        checkStatusOrThrow(env, LDB_MEMORY_ERROR, "Failed to get key data");
        return nullptr;
    }
    auto db = reinterpret_cast<ndb_holder *>(handle);

    uint8_t* buffer = nullptr;
    size_t buffer_len = 0;

    const cleveldb_status rc = leveldb_get_property(
        db,
        keyData.bytes(),
        keyData.usize(),
        &buffer,
        &buffer_len
    );
    checkStatusOrThrow(env, rc);

    if (buffer_len == 0) {
        return NewByteArrayFromBytes(env, nullptr, 0);
    }

    auto result = NewByteArrayFromBytes(env, buffer, buffer_len);

    leveldb_free_buffer(buffer);

    return result;
}

jbyteArray Java_com_edwardstock_leveldb_LevelDBNativeProvider_dbGet(
    JNIEnv* env,
    jobject,
    jlong dbHandle,
    jbyteArray key,
    jlong snapshotHandle
) {
    if (!checkHandleOrThrow(dbHandle, env)) {
        return nullptr;
    }
    ScopedByteArrayElements keyData(env, key);
    if (!keyData.isValid()) {
        checkStatusOrThrow(env, LDB_MEMORY_ERROR, "Failed to get key data");
        return nullptr;
    }

    auto db = reinterpret_cast<ndb_holder *>(dbHandle);
    auto* snapshot = reinterpret_cast<ndb_snapshot_holder *>(snapshotHandle);
    uint8_t* buffer = nullptr;
    size_t buffer_len = 0;

    const cleveldb_status rc = leveldb_get(
        db,
        keyData.bytes(),
        keyData.usize(),
        snapshot,
        &buffer,
        &buffer_len
    );
    checkStatusOrThrow(env, rc);

    if (buffer_len == 0) {
        return NewByteArrayFromBytes(env, nullptr, 0);
    }
    auto result = NewByteArrayFromBytes(env, buffer, buffer_len);
    leveldb_free_buffer(buffer);
    return result;
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_dbWrite(
    JNIEnv* env,
    jobject,
    jlong dbHandle,
    jboolean sync,
    jlong batchHandle
) {
    if (!checkHandleOrThrow(dbHandle, env)) {
        return;
    }
    if (!checkHandleOrThrow(batchHandle, env)) {
        return;
    }
    auto db = reinterpret_cast<ndb_holder *>(dbHandle);
    auto batch = reinterpret_cast<ndb_batch_holder *>(batchHandle);

    const cleveldb_status rc = leveldb_write(db, batch, sync == JNI_TRUE);
    checkStatusOrThrow(env, rc);
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_dbDelete(
    JNIEnv* env,
    jobject,
    jlong dbHandle,
    jboolean sync,
    jbyteArray key
) {
    if (!checkHandleOrThrow(dbHandle, env)) {
        return;
    }
    ScopedByteArrayElements keyData(env, key);
    if (!keyData.isValid()) {
        checkStatusOrThrow(env, LDB_MEMORY_ERROR, "Failed to get key data");
        return;
    }
    auto db = reinterpret_cast<ndb_holder *>(dbHandle);

    const cleveldb_status rc = leveldb_delete(
        db,
        keyData.bytes(),
        keyData.usize(),
        sync == JNI_TRUE
    );
    checkStatusOrThrow(env, rc);
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_dbPut(
    JNIEnv* env,
    jobject,
    jlong dbHandle,
    jboolean sync,
    jbyteArray key,
    jbyteArray value
) {
    if (!checkHandleOrThrow(dbHandle, env)) {
        return;
    }
    ScopedByteArrayElements keyData(env, key);
    if (!keyData.isValid()) {
        checkStatusOrThrow(env, LDB_MEMORY_ERROR, "Failed to get key data");
        return;
    }
    ScopedByteArrayElements valueData(env, value);
    if (!valueData.isValid()) {
        checkStatusOrThrow(env, LDB_MEMORY_ERROR, "Failed to get value data");
        return;
    }
    auto db = reinterpret_cast<ndb_holder *>(dbHandle);

    const cleveldb_status rc = leveldb_put(
        db,
        keyData.bytes(),
        keyData.usize(),
        valueData.bytes(),
        valueData.usize(),
        sync == JNI_TRUE
    );
    checkStatusOrThrow(env, rc);
}

void Java_com_edwardstock_leveldb_LevelDBNativeProvider_dbClose(
    JNIEnv* env,
    jobject,
    jlong dbHandle
) {
    if (dbHandle == 0L) {
        return;
    }
    auto db = reinterpret_cast<ndb_holder *>(dbHandle);

    leveldb_close(db);
}

#ifdef __cplusplus
}
#endif
