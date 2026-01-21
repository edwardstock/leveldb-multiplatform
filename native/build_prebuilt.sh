#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  cat <<'EOF'
Usage: build_prebuilt.sh [release|debug] [preset]

Builds native prebuilts using CMake presets via make_libs.py.

Arguments:
  build type  Optional. "release" (default) or "debug".
  preset      Optional. Build a single preset (e.g., manylinux2014-x64-release).

Environment:
  DOCKCROSS_DIR  Directory for dockcross wrappers (default: .dockcross-wrappers).

Flags:
  Pass through flags by calling make_libs.py directly for advanced options:
    ./make_libs.py --help
EOF
  exit 0
fi

BUILD_TYPE="${1:-release}"
PRESET="${2:-}"

ARGS=(--buildType "${BUILD_TYPE}" --dockcross-dir "${DOCKCROSS_DIR:-.dockcross-wrappers}")
if [[ -n "${PRESET}" ]]; then
  ARGS+=(--preset "${PRESET}")
fi

exec "${ROOT_DIR}/make_libs.py" "${ARGS[@]}"
