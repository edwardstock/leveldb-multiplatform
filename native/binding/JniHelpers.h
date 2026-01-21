#ifndef LEVELDB_JNI_HELPERS_H
#define LEVELDB_JNI_HELPERS_H

#include <cstdint>
#include <jni.h>
#include <string>
#include <vector>

// Lightweight RAII helpers for JNI string and byte-array element access.
// Usage:
// {
//   ScopedUtfChars s(env, jstr);
//   if (!s.isValid()) { /* handle error */ }
//   const char* c = s.c_str();
// }
// {
//   ScopedByteArrayElements b(env, jbyteArr);
//   if (!b.isValid()) { /* handle error */ }
//   jbyte* data = b.data();
//   jsize len = b.size();
// }

namespace leveldb_jni {

class ScopedUtfChars {
public:
    ScopedUtfChars(JNIEnv* env, jstring str, jboolean* isCopy = nullptr)
        : env_(env)
        , str_(str)
        , chars_(nullptr) {
        if (env_ && str_) {
            chars_ = env_->GetStringUTFChars(str_, isCopy);
        }
    }

    // non-copyable
    ScopedUtfChars(const ScopedUtfChars&) = delete;
    ScopedUtfChars& operator=(const ScopedUtfChars&) = delete;

    // movable
    ScopedUtfChars(ScopedUtfChars&& other) noexcept
         : env_(other.env_)
         , str_(other.str_)
         , chars_(other.chars_) {
         other.env_ = nullptr;
         other.str_ = nullptr;
         other.chars_ = nullptr;
     }

    ScopedUtfChars& operator=(ScopedUtfChars&& other) noexcept {
        if (this != &other) {
            release();
            env_ = other.env_;
            str_ = other.str_;
            chars_ = other.chars_;
            other.env_ = nullptr;
            other.str_ = nullptr;
            other.chars_ = nullptr;
        }
        return *this;
    }

    ~ScopedUtfChars() {
        release();
    }

    void release() {
        if (env_ && str_ && chars_) {
            env_->ReleaseStringUTFChars(str_, chars_);
        }
        env_ = nullptr;
        str_ = nullptr;
        chars_ = nullptr;
    }

    [[nodiscard]] const char* c_str() const { return chars_; }
    operator const char *() const { return chars_; }
    [[nodiscard]] bool isValid() const { return chars_ != nullptr; }
private:
    JNIEnv* env_;
    jstring str_;
    const char* chars_;
};

class ScopedByteArrayElements {
public:
    // mode is the same as ReleaseByteArrayElements mode parameter (0, JNI_COMMIT, JNI_ABORT)
    ScopedByteArrayElements(JNIEnv* env, jbyteArray arr, jint mode = 0)
        : env_(env)
        , arr_(arr)
        , elems_(nullptr)
        , len_(0)
        , mode_(mode) {
        if (env_ && arr_) {
            len_ = env_->GetArrayLength(arr_);
            elems_ = env_->GetByteArrayElements(arr_, nullptr);
        }
    }

    // non-copyable
    ScopedByteArrayElements(const ScopedByteArrayElements&) = delete;
    ScopedByteArrayElements& operator=(const ScopedByteArrayElements&) = delete;

    // movable
    ScopedByteArrayElements(ScopedByteArrayElements&& other) noexcept
         : env_(other.env_)
         , arr_(other.arr_)
         , elems_(other.elems_)
         , len_(other.len_)
         , mode_(other.mode_) {
         other.env_ = nullptr;
         other.arr_ = nullptr;
         other.elems_ = nullptr;
         other.len_ = 0;
         other.mode_ = 0;
     }

    ScopedByteArrayElements& operator=(ScopedByteArrayElements&& other) noexcept {
        if (this != &other) {
            release();
            env_ = other.env_;
            arr_ = other.arr_;
            elems_ = other.elems_;
            len_ = other.len_;
            mode_ = other.mode_;
            other.env_ = nullptr;
            other.arr_ = nullptr;
            other.elems_ = nullptr;
            other.len_ = 0;
            other.mode_ = 0;
        }
        return *this;
    }

    ~ScopedByteArrayElements() {
        release();
    }

    // Release with the stored mode (default was provided in constructor).
    void release() {
        if (env_ && arr_ && elems_) {
            env_->ReleaseByteArrayElements(arr_, elems_, mode_);
        }
        env_ = nullptr;
        arr_ = nullptr;
        elems_ = nullptr;
        len_ = 0;
    }

    // Force commit (copy back) and release elements
    void commit() {
        if (env_ && arr_ && elems_) {
            env_->ReleaseByteArrayElements(arr_, elems_, 0);
            elems_ = nullptr;
            len_ = 0;
        }
    }

    // Abort changes and release elements
    void abort() {
        if (env_ && arr_ && elems_) {
            env_->ReleaseByteArrayElements(arr_, elems_, JNI_ABORT);
            elems_ = nullptr;
            len_ = 0;
        }
    }

    // Detach without releasing (caller takes ownership of the pointer). Use with extreme caution.
    [[nodiscard]] jbyte* detach() {
        jbyte* tmp = elems_;
        elems_ = nullptr;
        len_ = 0;
        env_ = nullptr;
        arr_ = nullptr;
        return tmp;
    }

    [[nodiscard]] jbyte* data() const { return elems_; }

    [[nodiscard]] const uint8_t* bytes() const {
        return reinterpret_cast<const uint8_t *>(elems_);
    }

    [[nodiscard]] jsize size() const { return len_; }

    [[nodiscard]] size_t usize() const {
        return static_cast<size_t>(len_);
    }

    [[nodiscard]] bool isValid() const { return elems_ != nullptr; }
private:
    JNIEnv* env_;
    jbyteArray arr_;
    jbyte* elems_;
    jsize len_;
    jint mode_;
};

// --- new helpers: create a JVM byte[] from various byte-like sources ---
// These are small, inline convenience functions that create a jbyteArray,
// copy the provided bytes into it and return the new array (or nullptr on OOM).

inline jbyteArray NewByteArrayFromBytes(JNIEnv* env, const void* data, size_t len) noexcept {
    if (!env) return nullptr;
    auto n = static_cast<jsize>(len);
    jbyteArray arr = env->NewByteArray(n);
    if (!arr) return nullptr;
    if (n > 0 && data != nullptr) {
        env->SetByteArrayRegion(arr, 0, n, reinterpret_cast<const jbyte*>(data));
    }
    return arr;
}

inline jbyteArray NewByteArray(JNIEnv* env, const jbyte* data, size_t len) noexcept {
    return NewByteArrayFromBytes(env, data, len);
}

inline jbyteArray NewByteArray(JNIEnv* env, const uint8_t* data, size_t len) noexcept {
    return NewByteArrayFromBytes(env, data, len);
}

inline jbyteArray NewByteArray(JNIEnv* env, const char* data, size_t len) noexcept {
    return NewByteArrayFromBytes(env, data, len);
}

inline jbyteArray NewByteArray(JNIEnv* env, const std::string& s) noexcept {
    return NewByteArrayFromBytes(env, s.data(), s.size());
}

inline jbyteArray NewByteArray(JNIEnv* env, const std::vector<uint8_t>& v) noexcept {
    return NewByteArrayFromBytes(env, v.empty() ? nullptr : v.data(), v.size());
}

} // namespace leveldb_jni

#endif // LEVELDB_JNI_HELPERS_H
