//
// Created by edward on 11.12.2021.
//

#include "leveldb_logger.h"
#include <iostream>
#include <cstdarg>
int __log_write(int prio, const char *tag, const char *text) {
  fprintf(stdout, "%s: %s", tag, text);
  return 0;
}

int __log_print(int prio, const char *tag, const char *fmt, ...) {
  va_list argptr;
  va_start(argptr, fmt);
  vfprintf(stdout, fmt, argptr);
  va_end(argptr);

  return 0;
}

