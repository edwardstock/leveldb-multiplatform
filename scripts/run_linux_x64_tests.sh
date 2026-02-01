#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

IMAGE_NAME="${IMAGE_NAME:-leveldb-linux-x64-tests}"
IMAGE_TAG="${IMAGE_TAG:-jdk17}"
IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
PLATFORM="${PLATFORM:-linux/amd64}"

SDK_DIR="${SDK_DIR:-$HOME/.cache/leveldb-linux-x64/android-sdk}"
GRADLE_DIR="${GRADLE_DIR:-$HOME/.cache/leveldb-linux-x64/gradle}"

GRADLE_TASKS=("$@")
if [[ ${#GRADLE_TASKS[@]} -eq 0 ]]; then
  GRADLE_TASKS=(":leveldb:check")
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "❌ docker not found. Install Docker Desktop and retry." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "❌ Docker is not running. Start Docker Desktop and retry." >&2
  exit 1
fi

mkdir -p "${SDK_DIR}" "${GRADLE_DIR}"

if ! docker image inspect "${IMAGE}" >/dev/null 2>&1; then
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "${tmp_dir}"' EXIT
  cat > "${tmp_dir}/Dockerfile" <<'DOCKERFILE'
FROM eclipse-temurin:17-jdk-jammy
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash ca-certificates curl git unzip python3 \
    && rm -rf /var/lib/apt/lists/*
DOCKERFILE
  docker build --platform="${PLATFORM}" -t "${IMAGE}" "${tmp_dir}"
  rm -rf "${tmp_dir}"
  trap - EXIT
fi

docker run --rm \
  --platform="${PLATFORM}" \
  -v "${ROOT_DIR}:/work" \
  -v "${SDK_DIR}:/android-sdk" \
  -v "${GRADLE_DIR}:/gradle" \
  -w /work \
  -e ANDROID_SDK_ROOT=/android-sdk \
  -e ANDROID_HOME=/android-sdk \
  -e RUNNER_OS=Linux \
  -e RUNNER_TEMP=/tmp \
  -e HOME=/tmp/home \
  -e GRADLE_USER_HOME=/gradle \
  -e JAVA_TOOL_OPTIONS="-XX:+CreateCoredumpOnCrash -XX:ErrorFile=leveldb/hs_err_pid%p.log -Duser.home=/tmp/home -Duser.name=runner" \
  -e CI=1 \
  -u "$(id -u):$(id -g)" \
  "${IMAGE}" \
  bash -lc '
    set -euo pipefail
    mkdir -p "$HOME"
    export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
    bash ./.github/actions/setup-android-tools/install_cmdline_tools.sh
    bash ./.github/actions/setup-android-tools/install_sdk_packages.sh
    ./gradlew "$@"
  ' -- "${GRADLE_TASKS[@]}"
