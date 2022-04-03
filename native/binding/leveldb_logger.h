//
// Created by edward on 11.12.2021.
//

#ifndef LEVELDB_ANDROID_LEVELDB_LOGGER_H
#define LEVELDB_ANDROID_LEVELDB_LOGGER_H

int __log_write(int prio, const char* tag, const char* text);

/**
 * Writes a formatted string to the log, with priority `prio` and tag `tag`.
 * The details of formatting are the same as for
 * [printf(3)](http://man7.org/linux/man-pages/man3/printf.3.html).
 */
int __log_print(int prio, const char* tag, const char* fmt, ...);

#ifndef ANDROID
typedef enum log_priority {
  /** For internal use only.  */
  ANDROID_LOG_UNKNOWN = 0,
  /** The default priority, for internal use only.  */
  ANDROID_LOG_DEFAULT, /* only for SetMinPriority() */
  /** Verbose logging. Should typically be disabled for a release apk. */
  ANDROID_LOG_VERBOSE,
  /** Debug logging. Should typically be disabled for a release apk. */
  ANDROID_LOG_DEBUG,
  /** Informational logging. Should typically be disabled for a release apk. */
  ANDROID_LOG_INFO,
  /** Warning logging. For use with recoverable failures. */
  ANDROID_LOG_WARN,
  /** Error logging. For use with unrecoverable failures. */
  ANDROID_LOG_ERROR,
  /** Fatal logging. For use when aborting. */
  ANDROID_LOG_FATAL,
  /** For internal use only.  */
  ANDROID_LOG_SILENT, /* only for SetMinPriority(); must be last */
} log_priority;

#define __android_log_print __log_print
#define __android_log_write __log_write

#endif






#endif //LEVELDB_ANDROID_LEVELDB_LOGGER_H
