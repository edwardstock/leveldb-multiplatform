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
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include "com_edwardstock_leveldb_implementation_NativeIterator.h"
#include "leveldb/iterator.h"
#include "leveldb/slice.h"
#include "leveldb/options.h"
#include "leveldb/status.h"

#ifdef ANDROID
#include <android/log.h>
#else
#include "leveldb_logger.h"
#endif

extern "C" {

JNIEXPORT void JNICALL
Java_com_edwardstock_leveldb_implementation_NativeIterator_00024Companion_nclose
    (JNIEnv *env, jobject cself, jlong nit) {
  if (nit == 0) {
    return;
  }

  leveldb::Iterator *it = (leveldb::Iterator *) nit;

  leveldb::Status status = it->status();

  if (!status.ok()) {
    __android_log_print(ANDROID_LOG_INFO,
                        "com.github.leveldb:N",
                        "Iterator(%lld) about to close with status: %s",
                        nit,
                        status.ToString().data());
  }

  delete it;
}

JNIEXPORT jboolean JNICALL
Java_com_edwardstock_leveldb_implementation_NativeIterator_00024Companion_nvalid
    (JNIEnv *env, jobject cself, jlong nit) {
  leveldb::Iterator *it = (leveldb::Iterator *) nit;

  jboolean retval = (jboolean) it->Valid();

  leveldb::Status status = it->status();

  if (!status.ok()) {
    __android_log_print(ANDROID_LOG_INFO,
                        "com.github.leveldb:N",
                        "Iterator(%lld): %s",
                        nit,
                        status.ToString().data());

    // TODO: Probably throw Exception here?
  }

  return retval;
}

JNIEXPORT void JNICALL
Java_com_edwardstock_leveldb_implementation_NativeIterator_00024Companion_nseek
    (JNIEnv *env, jobject cself, jlong nit, jbyteArray key) {
  leveldb::Iterator *it = (leveldb::Iterator *) nit;

  const char *keyData = (char *) env->GetByteArrayElements(key, 0);
  leveldb::Slice keySlice(keyData, (size_t) env->GetArrayLength(key));

  it->Seek(keySlice);

  env->ReleaseByteArrayElements(key, (jbyte *) keyData, 0);

  leveldb::Status status = it->status();

  if (!status.ok()) {
    __android_log_print(ANDROID_LOG_INFO,
                        "com.github.leveldb:N",
                        "Iterator(%lld): %s",
                        nit,
                        status.ToString().data());

    // TODO: Probably throw Exception here?
  }
}

JNIEXPORT void JNICALL
Java_com_edwardstock_leveldb_implementation_NativeIterator_00024Companion_nseekToFirst
    (JNIEnv *env, jobject cself, jlong nit) {
  leveldb::Iterator *it = (leveldb::Iterator *) nit;

  it->SeekToFirst();

  leveldb::Status status = it->status();

  if (!status.ok()) {
    __android_log_print(ANDROID_LOG_INFO,
                        "com.github.leveldb:N",
                        "Iterator(%lld): %s",
                        nit,
                        status.ToString().data());

    // TODO: Probably throw Exception here?
  }
}

JNIEXPORT void JNICALL
Java_com_edwardstock_leveldb_implementation_NativeIterator_00024Companion_nseekToLast
    (JNIEnv *env, jobject cself, jlong nit) {
  leveldb::Iterator *it = (leveldb::Iterator *) nit;

  it->SeekToLast();

  leveldb::Status status = it->status();

  if (!status.ok()) {
    __android_log_print(ANDROID_LOG_INFO,
                        "com.github.leveldb:N",
                        "Iterator(%lld): %s",
                        nit,
                        status.ToString().data());

    // TODO: Probably throw Exception here?
  }
}

JNIEXPORT void JNICALL
Java_com_edwardstock_leveldb_implementation_NativeIterator_00024Companion_nnext
    (JNIEnv *env, jobject cself, jlong nit) {
  leveldb::Iterator *it = (leveldb::Iterator *) nit;

  it->Next();

  leveldb::Status status = it->status();

  if (!status.ok()) {
    __android_log_print(ANDROID_LOG_INFO,
                        "com.github.leveldb:N",
                        "Iterator(%lld): %s",
                        nit,
                        status.ToString().data());

    // TODO: Probably throw Exception here?
  }
}

JNIEXPORT void JNICALL
Java_com_edwardstock_leveldb_implementation_NativeIterator_00024Companion_nprev
    (JNIEnv *env, jobject cself, jlong nit) {
  leveldb::Iterator *it = (leveldb::Iterator *) nit;

  it->Prev();

  leveldb::Status status = it->status();

  if (!status.ok()) {
    __android_log_print(ANDROID_LOG_INFO,
                        "com.github.leveldb:N",
                        "Iterator(%lld): %s",
                        nit,
                        status.ToString().data());

    // TODO: Probably throw Exception here?
  }
}

JNIEXPORT jbyteArray JNICALL
Java_com_edwardstock_leveldb_implementation_NativeIterator_00024Companion_nkey
    (JNIEnv *env, jobject cself, jlong nit) {
  leveldb::Iterator *it = (leveldb::Iterator *) nit;

  if (!it->Valid()) {
    return 0;
  }

  leveldb::Slice key = it->key();

  leveldb::Status status = it->status();

  if (!status.ok()) {
    __android_log_print(ANDROID_LOG_INFO,
                        "com.github.leveldb:N",
                        "Iterator(%lld): %s",
                        nit,
                        status.ToString().data());

    // TODO: Probably throw Exception here?

    return 0;
  }

  jbyteArray retval = env->NewByteArray(key.size());

  env->SetByteArrayRegion(retval, 0, key.size(), (jbyte *) key.data());

  return retval;
}

JNIEXPORT jbyteArray JNICALL
Java_com_edwardstock_leveldb_implementation_NativeIterator_00024Companion_nvalue
    (JNIEnv *env, jobject cself, jlong nit) {
  leveldb::Iterator *it = (leveldb::Iterator *) nit;

  if (!it->Valid()) {
    return 0;
  }

  leveldb::Slice value = it->value();

  leveldb::Status status = it->status();

  if (!status.ok()) {
    __android_log_print(ANDROID_LOG_INFO,
                        "com.github.leveldb:N",
                        "Iterator(%lld): %s",
                        nit,
                        status.ToString().data());

    // TODO: Probably throw Exception here?

    return 0;
  }

  jbyteArray retval = env->NewByteArray(value.size());

  env->SetByteArrayRegion(retval, 0, value.size(), (jbyte *) value.data());

  return retval;
}

} // extern C
