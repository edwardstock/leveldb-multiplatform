project(leveldb_jni
        LANGUAGES CXX
        )
cmake_minimum_required(VERSION 3.10)

set(CMAKE_CXX_STANDARD 14)

set(LEVELDB_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LEVELDB_BUILD_BENCHMARKS OFF CACHE BOOL "" FORCE)
set(LEVELDB_INSTALL OFF CACHE BOOL "" FORCE)
#set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)

if (NOT WIN32)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -fexceptions -fno-rtti -fPIC -Wno-unused-variable -pedantic -Wno-unused-parameter -Wno-format -Wno-shift-count-overflow")
    set(CMAKE_CXX_FLAGS_DEBUG "-g -O0")
    set(CMAKE_CXX_FLAGS_RELEASE "-O3")
else ()

endif ()


add_subdirectory(leveldb)


set(JNI_SOURCES
        ${CMAKE_CURRENT_SOURCE_DIR}/binding/com_edwardstock_leveldb_implementation_NativeIterator.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/binding/com_edwardstock_leveldb_implementation_NativeIterator.h
        ${CMAKE_CURRENT_SOURCE_DIR}/binding/com_edwardstock_leveldb_implementation_NativeLevelDB.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/binding/com_edwardstock_leveldb_implementation_NativeLevelDB.h
        ${CMAKE_CURRENT_SOURCE_DIR}/binding/com_edwardstock_leveldb_implementation_NativeWriteBatch.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/binding/com_edwardstock_leveldb_implementation_NativeWriteBatch.h
        ${CMAKE_CURRENT_SOURCE_DIR}/binding/leveldb_logger.h
        ${CMAKE_CURRENT_SOURCE_DIR}/binding/leveldb_logger.cpp
        )

add_library(${PROJECT_NAME} SHARED ${JNI_SOURCES})


if (ANDROID_PLATFORM)
    add_definitions(-D__ANDROID__)

    target_link_libraries(${PROJECT_NAME} leveldb log dl)
else ()
    find_package(JNI)
    message(STATUS "${JAVA_HOME}")
        message(STATUS "${JNI_INCLUDE_DIRS}")
    if (JNI_FOUND)

        target_link_libraries(${PROJECT_NAME} ${JAVA_JVM_LIBRARY})
        target_include_directories(${PROJECT_NAME} PRIVATE ${JNI_INCLUDE_DIRS})
    else ()
        message(FATAL_ERROR "JNI not found")
    endif ()

    target_link_libraries(${PROJECT_NAME} leveldb)
    if (NOT WIN32)
        target_link_libraries(${PROJECT_NAME} dl)
    endif ()
endif ()

