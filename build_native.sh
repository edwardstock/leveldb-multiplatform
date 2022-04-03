#!/usr/bin/env bash

set -e
set -x

_CMAKE_VERSION=3.18.1
_ANDROID_HOME=$1
_NDK_VERSION=$2
_SOURCE_DIR=$3
_OUTPUT_DIR=$4
_BUILD_TYPE=$5
_ABIS=(x86 x86_64)

_BUILD_DIR=${PWD}/.cxx

CMAKE_BIN="${_ANDROID_HOME}/cmake/${_CMAKE_VERSION}/bin/cmake"
if [ ! -f "${CMAKE_BIN}" ];
then
  echo "CMake didn't found in ${CMAKE_BIN}"
  exit 1
fi

mkdir -p $_BUILD_DIR

for abi_idx in ${!_ABIS[*]}; do

  if [ "$(ls -A ${_OUTPUT_DIR}/${_ABIS[$abi_idx]})" ];
  then
    echo "${_ABIS[$abi_idx]} - already exist"
    continue
  fi


  ${CMAKE_BIN} -B"${_BUILD_DIR}" \
  -S"${_SOURCE_DIR}" \
  -DCMAKE_BUILD_TYPE="${_BUILD_TYPE}" \
  -DCMAKE_TOOLCHAIN_FILE="${_ANDROID_HOME}/ndk/${_NDK_VERSION}/build/cmake/android.toolchain.cmake" \
  -DANDROID_PLATFORM=android-21 \
  -DANDROID_ABI="${_ABIS[$abi_idx]}" \
  -DCMAKE_ARCHIVE_OUTPUT_DIRECTORY="${_OUTPUT_DIR}/${_ABIS[$abi_idx]}" \
  -DCMAKE_LIBRARY_OUTPUT_DIRECTORY="${_OUTPUT_DIR}/${_ABIS[$abi_idx]}"

  ${CMAKE_BIN} --build ${_BUILD_DIR} --target all
done

#-DCMAKE_TOOLCHAIN_FILE=$ANDROID_HOME/ndk/21.1.6352462/build/cmake/android.toolchain.cmake -DANDROID_ABI=x86