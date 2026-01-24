#!/usr/bin/env bash
set -euo pipefail

yes | sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --licenses >/dev/null || true
sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" "platform-tools" "platforms;android-36" "ndk;29.0.14206865" "cmake;4.1.2"
