cmake_minimum_required(VERSION 3.10...4.1)
project(leveldb_jni
    VERSION 2.0.0
    LANGUAGES CXX
)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(LEVELDB_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LEVELDB_BUILD_BENCHMARKS OFF CACHE BOOL "" FORCE)
set(LEVELDB_INSTALL OFF CACHE BOOL "" FORCE)

include(modules/output-layout.cmake)

function(append_link_flags TARGET FLAGS)
    get_target_property(_existing ${TARGET} LINK_FLAGS)
    if(NOT _existing)
        set(_existing "")
    endif()
    set_target_properties(${TARGET} PROPERTIES LINK_FLAGS "${_existing} ${FLAGS}")
endfunction()

if (NOT WIN32)
    add_compile_options(
        -Wall
        -fexceptions
        -fno-rtti
        -fPIC
        -Wno-unused-variable
        -pedantic
        -Wno-unused-parameter
        -Wno-unused-but-set-variable
        -Wno-format
        -Wno-shift-count-overflow
    )
    add_compile_options(
        "$<$<CONFIG:Debug>:-g>"
        "$<$<CONFIG:Debug>:-O0>"
        "$<$<CONFIG:Release>:-O3>"
    )
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)
endif ()


add_subdirectory(leveldb)
if (TARGET leveldbutil)
    set_target_properties(leveldbutil PROPERTIES
        EXCLUDE_FROM_ALL TRUE
        EXCLUDE_FROM_DEFAULT_BUILD TRUE)
endif()

set(KOTLIN_CINTEROP_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/cinterop/cleveldb.h
    ${CMAKE_CURRENT_SOURCE_DIR}/cinterop/cleveldb.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/shared/ndb_holder.h
)
add_library(cleveldb STATIC ${KOTLIN_CINTEROP_SOURCES})
target_link_libraries(cleveldb leveldb)
target_include_directories(cleveldb PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/shared)

target_compile_definitions(cleveldb PUBLIC $<$<CONFIG:Debug>:CLEVELDB_DEBUG=1>)

if (NOT CMAKE_SYSTEM_NAME STREQUAL "iOS")
    set(JNI_SOURCES
        ${CMAKE_CURRENT_SOURCE_DIR}/binding/LevelDBNativeProvider.h
        ${CMAKE_CURRENT_SOURCE_DIR}/binding/LevelDBNativeProvider.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/binding/JniHelpers.h
        ${CMAKE_CURRENT_SOURCE_DIR}/shared/ndb_holder.h
        ${CMAKE_CURRENT_SOURCE_DIR}/binding/leveldb_logger.h
        ${CMAKE_CURRENT_SOURCE_DIR}/binding/leveldb_logger.cpp
    )
    add_library(${PROJECT_NAME} SHARED ${JNI_SOURCES})
    target_include_directories(${PROJECT_NAME} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/shared)
    target_link_libraries(${PROJECT_NAME} cleveldb)
endif ()


if (ANDROID_PLATFORM)
    add_definitions(-D__ANDROID__)

    target_link_libraries(${PROJECT_NAME} leveldb log dl)
elseif (CMAKE_SYSTEM_NAME STREQUAL "iOS")
    # No JNI on iOS; cleveldb is enough for Kotlin/Native.
else ()
    find_package(JNI)
    if (JNI_FOUND)
        target_include_directories(${PROJECT_NAME} PRIVATE ${JNI_INCLUDE_DIRS})
    elseif (JAVA_INCLUDE_PATH)
        file(GLOB_RECURSE JAVA_SUBDIRS
            LIST_DIRECTORIES true
            "${JAVA_INCLUDE_PATH}/*"
        )
        set(_include_dirs "")
        foreach(path IN LISTS JAVA_SUBDIRS)
            if(IS_DIRECTORY "${path}")
                list(APPEND _include_dirs "${path}")
            endif()
        endforeach()
#        message(FATAL_ERROR ${_include_dirs})

        target_include_directories(${PROJECT_NAME} PRIVATE ${JAVA_INCLUDE_PATH} ${_include_dirs})
    else ()
        message(FATAL_ERROR "JNI not found")
    endif ()

    target_link_libraries(${PROJECT_NAME} leveldb)
    if (NOT WIN32)
        target_link_libraries(${PROJECT_NAME} dl)
    endif ()
endif ()


if (PREBUILT_DIR)
    if (TARGET ${PROJECT_NAME})
        set_standard_output_dirs(${PROJECT_NAME} ${PREBUILT_DIR})
    endif ()
    set_standard_output_dirs(leveldb ${PREBUILT_DIR})
    set_standard_output_dirs(cleveldb ${PREBUILT_DIR})
endif ()

if (APPLE AND NOT CMAKE_SYSTEM_NAME STREQUAL "iOS")
    set_target_properties(cleveldb PROPERTIES
        MACOSX_DEPLOYMENT_TARGET "11.0"
    )

    # set minimum deployment target for macOS
    target_compile_options(leveldb PRIVATE -mmacosx-version-min=11.0)
    append_link_flags(leveldb "-mmacosx-version-min=11.0")
    target_compile_options(cleveldb PRIVATE -mmacosx-version-min=11.0)
    append_link_flags(cleveldb "-mmacosx-version-min=11.0")
endif ()
