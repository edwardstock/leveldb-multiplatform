# --- Normalize OS -------------------------------------------------------------
function(_normalize_os OUT_VAR)
    set(os "${ARGV1}")
    if(NOT os)
        set(os "${CMAKE_SYSTEM_NAME}")
    endif()

    string(TOLOWER "${os}" os)
    if(os MATCHES "darwin|mac|osx")
        set(os "macos")
    elseif(os MATCHES "windows|mingw|msys|cygwin")
        set(os "windows")
    elseif(os MATCHES "linux")
        set(os "linux")
    elseif(os MATCHES "android")
        set(os "android")
    elseif(os MATCHES "ios")
        string(TOLOWER "${CMAKE_OSX_SYSROOT}" _sysroot)
        if(_sysroot MATCHES "iphonesimulator")
            set(os "ios-simulator")
        elseif(_sysroot MATCHES "iphoneos")
            set(os "ios-device")
        else()
            set(os "ios")
        endif()
    elseif(os MATCHES "tvos")
        set(os "tvos")
    elseif(os MATCHES "watchos")
        set(os "watchos")
    else()
        # fine, be weird
        string(REPLACE " " "_" os "${os}")
    endif()

    set(${OUT_VAR} "${os}" PARENT_SCOPE)
endfunction()

# --- Normalize ARCH -----------------------------------------------------------
# Accepts optional explicit arch string as arg2; otherwise uses CMAKE_SYSTEM_PROCESSOR.
# On Apple, respects CMAKE_OSX_ARCHITECTURES if set (arm64;x86_64 -> "universal").
function(_normalize_arch OUT_VAR)
    set(arch_in "${ARGV1}")
    if(NOT arch_in)
        set(arch_in "${CMAKE_SYSTEM_PROCESSOR}")
    endif()

    # Apple universal build?
    if(APPLE AND CMAKE_OSX_ARCHITECTURES)
        list(LENGTH CMAKE_OSX_ARCHITECTURES _len)
        if(_len GREATER 1)
            set(${OUT_VAR} "universal" PARENT_SCOPE)
            return()
        else()
            list(GET CMAKE_OSX_ARCHITECTURES 0 arch_in)
        endif()
    endif()

    string(TOLOWER "${arch_in}" arch)
    string(REPLACE "-" "_" arch "${arch}")
    string(REPLACE " " "_" arch "${arch}")

    # Canonical mapping
    if(arch MATCHES "^(x8664|x86_64|amd64|x64)$")
        set(arch "x86_64")
    elseif(arch MATCHES "^(x86|i[3456]86|win32)$")
        set(arch "x86")
    elseif(arch MATCHES "^(aarch64|arm64)$")
        set(arch "arm64")
    elseif(arch MATCHES "^(armv7|armv7l|armhf)$")
        set(arch "armv7")
    elseif(arch MATCHES "^(armv6|armv6l)$")
        set(arch "armv6")
    elseif(arch MATCHES "^ppc64le$")
        set(arch "ppc64le")
    elseif(arch MATCHES "^s390x$")
        set(arch "s390x")
    elseif(arch MATCHES "^riscv64$")
        set(arch "riscv64")
    endif()

    set(${OUT_VAR} "${arch}" PARENT_SCOPE)
endfunction()

# --- Public: compute canonical OS/ARCH pair ----------------------------------
function(get_canonical_platform OUT_OS OUT_ARCH)
    _normalize_os(_os "${ARGV2}")     # optional explicit OS as arg3
    _normalize_arch(_arch "${ARGV3}") # optional explicit ARCH as arg4
    set(${OUT_OS}   "${_os}"   PARENT_SCOPE)
    set(${OUT_ARCH} "${_arch}" PARENT_SCOPE)
endfunction()

# --- Public: set per-target output dirs --------------------------------------
# Usage:
#   add_library(foo SHARED ...)
#   set_standard_output_dirs(foo "${CMAKE_BINARY_DIR}/out")
#
# Produces: <base>/<os>/<arch>/$<CONFIG>/{foo.dll,libfoo.a,...}
function(set_standard_output_dirs TARGET BASE_DIR)
    if(NOT TARGET "${TARGET}")
        message(FATAL_ERROR "Target '${TARGET}' does not exist")
    endif()

    get_canonical_platform(_os _arch)

    # Single expression works for single- and multi-config
    set(_out "${BASE_DIR}/${_os}/${_arch}/$<CONFIG>")

    set_target_properties(${TARGET} PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY "${_out}"   # .exe/.dll
        LIBRARY_OUTPUT_DIRECTORY "${_out}"   # .so/.dylib
        ARCHIVE_OUTPUT_DIRECTORY "${_out}"   # .a/.lib import libs
        PDB_OUTPUT_DIRECTORY     "${_out}"
    )

    # Windows nicety: ditch 'lib' prefix on DLLs unless you like chaos
    if(CMAKE_SYSTEM_NAME STREQUAL "Windows")
        get_target_property(_type ${TARGET} TYPE)
        if(_type STREQUAL "SHARED_LIBRARY" OR _type STREQUAL "MODULE_LIBRARY")
            set_target_properties(${TARGET} PROPERTIES PREFIX "")
        endif()
    endif()

    message(STATUS "[layout] ${TARGET} -> ${_out}")
endfunction()

# --- Optional: set global defaults for all subsequent targets ----------------
# Use if you’re too tired to call the per-target helper repeatedly.
function(set_project_output_layout BASE_DIR)
    get_canonical_platform(_os _arch)
    set(_out "${BASE_DIR}/${_os}/${_arch}/$<CONFIG>")

    # Affect targets created after this point
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${_out}" PARENT_SCOPE)
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${_out}" PARENT_SCOPE)
    set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY "${_out}" PARENT_SCOPE)
    set(CMAKE_PDB_OUTPUT_DIRECTORY     "${_out}" PARENT_SCOPE)

    message(STATUS "[layout] project default -> ${_out}")
endfunction()
