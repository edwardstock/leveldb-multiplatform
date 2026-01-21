//
// Created by Eduard Maximovich on 22.10.2025.
//

#ifndef LEVELDB_JNI_ANDROID_LOGGER_H
#define LEVELDB_JNI_ANDROID_LOGGER_H
#include "leveldb/env.h"

// Redirects leveldb's logging to the Android logger.
class AndroidLogger final : public leveldb::Logger {
public:
    void Logv(const char* format, va_list ap) override {
        //        __android_log_vprint(ANDROID_LOG_INFO, "com.edwardstock.leveldb:N", format, ap);
    }
};

#endif //LEVELDB_JNI_ANDROID_LOGGER_H
