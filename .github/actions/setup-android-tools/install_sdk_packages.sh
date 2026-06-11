#!/usr/bin/env bash
set -euo pipefail

yes | sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --licenses >/dev/null || true
# API 37 is published only as the versioned package platforms;android-37.0 — there is no bare
# platforms;android-37 in the SDK repo (see actions/runner-images#13859).
sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" "platform-tools" "platforms;android-37.0" "ndk;29.0.14206865" "cmake;4.1.2"
# AGP resolves compileSdk 37 to the target hash "android-37", but the platform installs into
# platforms/android-37.0. Alias it so Gradle can find the platform. -sfn keeps it idempotent across
# cache-restored runs (overwrites a stale link instead of nesting one inside the target dir).
ln -sfn android-37.0 "${ANDROID_SDK_ROOT}/platforms/android-37"
