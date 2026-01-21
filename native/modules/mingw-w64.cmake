# mingw-w64.cmake
# Cross-compile Windows binaries from macOS (Apple Silicon) using Homebrew mingw-w64
# Usage:
#   cmake -G Ninja -S . -B build-win \
#     -DCMAKE_TOOLCHAIN_FILE=toolchains/mingw-w64.cmake \
#     -DMINGW_TRIPLET=x86_64-w64-mingw32   # or aarch64-w64-mingw32
#
# Optional:
#   -DMINGW_PREFIX=/opt/homebrew/opt/mingw-w64

cmake_minimum_required(VERSION 3.20)

# 1) Tell CMake we're targeting Windows
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_VERSION 10)

# 2) Choose the MinGW target triplet
#    Common values: x86_64-w64-mingw32, aarch64-w64-mingw32, i686-w64-mingw32
set(MINGW_TRIPLET "x86_64-w64-mingw32" CACHE STRING "MinGW-w64 target triplet")
# 3) Where Homebrew puts mingw-w64. Adjust if you installed it elsewhere.
#    On Apple Silicon, Homebrew prefix is /opt/homebrew. On Intel Macs it's /usr/local.
if(NOT DEFINED MINGW_PREFIX AND DEFINED ENV{MINGW_PREFIX})
    set(MINGW_PREFIX "$ENV{MINGW_PREFIX}" CACHE PATH "MinGW-w64 installation prefix")
endif()
if(NOT DEFINED MINGW_PREFIX)
    set(MINGW_PREFIX "/opt/homebrew/opt/mingw-w64" CACHE PATH "MinGW-w64 installation prefix")
endif()

# 4) Resolve compiler paths
set(_MINGW_BIN "${MINGW_PREFIX}/bin")

# Allow user to override explicitly via environment or cache if they’re fancy.
set(CMAKE_C_COMPILER   "${_MINGW_BIN}/${MINGW_TRIPLET}-gcc"  CACHE FILEPATH "C compiler")
set(CMAKE_CXX_COMPILER "${_MINGW_BIN}/${MINGW_TRIPLET}-g++"  CACHE FILEPATH "C++ compiler")
set(CMAKE_RC_COMPILER  "${_MINGW_BIN}/${MINGW_TRIPLET}-windres" CACHE FILEPATH "Resource compiler")

# Optional binutils; not strictly required but nice to have wired up.
set(CMAKE_AR           "${_MINGW_BIN}/${MINGW_TRIPLET}-ar"      CACHE FILEPATH "Archiver")
set(CMAKE_RANLIB       "${_MINGW_BIN}/${MINGW_TRIPLET}-ranlib"  CACHE FILEPATH "Ranlib")
set(CMAKE_STRIP        "${_MINGW_BIN}/${MINGW_TRIPLET}-strip"   CACHE FILEPATH "Strip")
set(CMAKE_DLLTOOL      "${_MINGW_BIN}/${MINGW_TRIPLET}-dlltool" CACHE FILEPATH "DLL Tool")

# 5) Figure out the sysroot from the compiler itself so we don't hardcode Homebrew’s keg paths
execute_process(
    COMMAND "${CMAKE_C_COMPILER}" -print-sysroot
    OUTPUT_VARIABLE MINGW_SYSROOT
    OUTPUT_STRIP_TRAILING_WHITESPACE
    ERROR_QUIET
)

# 6) Where to look for headers and libs when find_package/find_library run
#    Prefer the compiler's reported sysroot. Fall back to a sane guess.
if(MINGW_SYSROOT)
    set(CMAKE_FIND_ROOT_PATH "${MINGW_SYSROOT}")
else()
    # Typical layout: <prefix>/${MINGW_TRIPLET}
    set(CMAKE_FIND_ROOT_PATH "${MINGW_PREFIX}/${MINGW_TRIPLET}")
endif()

# 7) Teach CMake to search headers and libs inside the sysroot, but not programs
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)

# 8) Some reasonable defaults for Windows builds
if(MINGW_TRIPLET MATCHES "^(x86_64|aarch64|i686)-")
    string(REGEX REPLACE "-.*" "" _trip_arch "${MINGW_TRIPLET}")
    if(_trip_arch STREQUAL "aarch64")
        set(_canon_arch "arm64")
    elseif(_trip_arch STREQUAL "i686")
        set(_canon_arch "x86")
    else()
        set(_canon_arch "${_trip_arch}")  # x86_64 stays x86_64
    endif()
    set(CMAKE_SYSTEM_PROCESSOR "${_canon_arch}" CACHE STRING "Target CPU" FORCE)
endif()
add_definitions(-DWIN32 -D_WINDOWS -DUNICODE -D_UNICODE)

# Make sure we produce .exe/.dll as expected
set(CMAKE_EXECUTABLE_SUFFIX ".exe")
set(CMAKE_SHARED_LIBRARY_SUFFIX ".dll")
set(CMAKE_IMPORT_LIBRARY_SUFFIX ".lib")

# 9) Linker niceties. Static libstdc++/libgcc makes life easier on bare Windows boxes.
#    Comment these if you explicitly want dynamic runtimes.
set(_rt_flags "-static-libgcc -static-libstdc++")
string(APPEND CMAKE_EXE_LINKER_FLAGS_INIT " ${_rt_flags}")
string(APPEND CMAKE_SHARED_LINKER_FLAGS_INIT " ${_rt_flags}")
string(APPEND CMAKE_MODULE_LINKER_FLAGS_INIT " ${_rt_flags}")

# 10) Default to Ninja if generator not specified. You can still pass -G "Unix Makefiles" if you insist.
if(NOT CMAKE_GENERATOR)
    set(CMAKE_GENERATOR "Ninja" CACHE INTERNAL "" FORCE)
endif()

# 11) Print a tiny summary so you can tell if CMake did something silly.
message(STATUS "MinGW-w64 triplet    : ${MINGW_TRIPLET}")
message(STATUS "MinGW-w64 prefix     : ${MINGW_PREFIX}")
message(STATUS "MinGW-w64 sysroot    : ${CMAKE_FIND_ROOT_PATH}")
message(STATUS "C compiler           : ${CMAKE_C_COMPILER}")
message(STATUS "C++ compiler         : ${CMAKE_CXX_COMPILER}")
message(STATUS "RC compiler          : ${CMAKE_RC_COMPILER}")
