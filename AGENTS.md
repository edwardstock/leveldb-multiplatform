# Agents Guide

Use this project primer when making automated edits.

## What this repo is

- Kotlin Multiplatform bindings for LevelDB with JVM/Android JNI and Kotlin/Native (desktop/iOS).
- Modules: `leveldb` (KMP core), `leveldb-android-native` (JNI AAR), examples: `android-example`, `ios-example`.

## Build and test quickstart

- JDK 17 expected. Android SDK/NDK/CMake from `gradle/libs.versions.toml` (AGP 9.0.0, NDK 29.0.14206865, CMake 4.1.2).
- Primary CI tasks: `./gradlew :leveldb:check` (Linux, macOS), `./gradlew :leveldb:mingwX64Test` (Windows). See `.github/workflows/ci.yml`.
- Native JNI debug build example: `./gradlew :leveldb-android-native:externalNativeBuildDebug` (prebuilts normally used; build only if needed).

## Native prebuilts

- Prebuilt libs live under `native/prebuilt` (see `native/README.md`). Avoid regenerating unless required.
- Wrapper scripts: `native/build_prebuilt.sh [release|debug] [preset]` and `native/make_libs.py` manage CMake presets; use only when you must rebuild
  host/cross artifacts.
- Don’t edit generated/prebuilt outputs (`native/build`, `native/prebuilt`, `leveldb-android-native/build`, `leveldb/build`); prefer source or CMake
  input changes.

## Android/AGP 9.0.0 caveats

- AGP 9 uses the new Android DSL; legacy `android {}` accessors may be deprecated/removed. Prefer `com.android.kotlin.multiplatform.library` plugin
  for Android KMP.
- AGP 9 is not compatible with applying `com.android.library` alongside `org.jetbrains.kotlin.multiplatform` (see AGP release notes). Keep Android
  wiring inside the KMP Android target blocks or dedicated Android-only modules.
- When editing Gradle files, favor the versions/catalog values in `gradle/libs.versions.toml` and keep new DSL patterns.

## Publishing guardrails

- Publishing is CI-driven (`.github/workflows/publish.yml`); requires Maven Central and GPG secrets. Do not run publish locally without creds.
- Local scripts `publish_local.sh` / `publish_remote.sh` expect the same env vars (see workflow for names) and will sign artifacts.

## Safe-change checklist

- Leave generated/prebuilt outputs untouched; commit only source/Gradle/CMake definitions.
- Keep Kotlin/Native cinterop def files and JNI headers in `native/cinterop` and `native/binding` aligned with native libs.
- Maintain tests: `leveldb` module has common/jvm/native tests; add/adjust tests when changing APIs or native interactions.
- Respect API contracts (e.g., return null for missing properties instead of throwing) and keep throwable filters in sync across expect/actuals.
