#!/usr/bin/env bash
set -euo pipefail

if command -v sdkmanager >/dev/null 2>&1; then
  exit 0
fi

mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"
case "${RUNNER_OS}" in
  Linux) TOOLS_ZIP="commandlinetools-linux-14742923_latest.zip" ;;
  macOS) TOOLS_ZIP="commandlinetools-mac-14742923_latest.zip" ;;
  *) echo "Unsupported OS: ${RUNNER_OS}" >&2; exit 1 ;;
esac
export TOOLS_ZIP

curl -fsSL "https://dl.google.com/android/repository/${TOOLS_ZIP}" -o "${RUNNER_TEMP}/${TOOLS_ZIP}"
if command -v unzip >/dev/null 2>&1; then
  unzip -q "${RUNNER_TEMP}/${TOOLS_ZIP}" -d "${ANDROID_SDK_ROOT}/cmdline-tools"
else
  python3 -c 'import os, zipfile; sdk_root=os.environ["ANDROID_SDK_ROOT"]; zip_path=os.path.join(os.environ["RUNNER_TEMP"], os.environ["TOOLS_ZIP"]); dest_dir=os.path.join(sdk_root, "cmdline-tools"); zipfile.ZipFile(zip_path, "r").extractall(dest_dir)'
fi
mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
