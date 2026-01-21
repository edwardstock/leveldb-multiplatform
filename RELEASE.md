# Release notes
## 2.0.0

- Major Kotlin Multiplatform refactor with new `leveldb` KMP module, APIs, and expanded tests.
- Restructured examples: new `android-example` and `ios-example`, removed legacy `example`.
- Build system updates: Gradle wrapper, version catalog, and project settings cleanup.
- Android updates: support for NDK `29.0.14206865`.
- Native build overhaul: new CMake presets/layout, host-compatible build scripts, and refreshed prebuilt output structure.
- JNI/native bindings reorganized with new `binding`, `cinterop`, and `shared` sources.
- Updated LevelDB submodule and licensing cleanup (consolidated LICENSE and added third_party notices).

## 1.0.2

- Updated leveldb to the latest master
- Added `@Synchronized` to levelDbContext
- Added `coLevelDbContext` with mutex lock

## 1.0.1

- Added `forEachKeys` and `forEachValues` extensions to `LevelDB` 

## 1.0.0
 - Initial release after migration to new repo


# Old release notes (stacked)

## 2.1.0

- Fully converted to kotlin
- Updated tests
- Updated gradle to 7.0
- Target SDK now 31

## 2.0.1

- Updated dependencies
- Cleanup repo
- Updated test

## 2.0.0

- Fully refactored build process
- Added latest version of leveldb (as it's stable)
- More api variation
