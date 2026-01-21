# iOS Example (Compose Multiplatform)

This is a minimal iOS app that exercises the LevelDB Kotlin library using Compose Multiplatform. It stores text items in LevelDB and lists them on screen.

## What you should see

- A simple screen titled "LevelDB".
- A text field with a Save button.
- Saved items appear in a list with their IDs; tap Delete to remove them.

## Build prerequisites

- Xcode + iOS Simulator installed.
- XcodeGen (already used here): `brew install xcodegen`.
- Gradle wrapper in repo root.

## Build and run (Simulator)

1) Build the Kotlin/Native framework for the simulator:

```sh
./gradlew :ios-example:linkDebugFrameworkIosSimulatorArm64
```

2) Generate the Xcode project (if needed):

```sh
xcodegen generate -s ios-example/iosApp/xcodegen.yml
```

3) Build with xcodebuild:

```sh
xcodebuild -project ios-example/iosApp/LevelDBExampleApp.xcodeproj \
  -scheme LevelDBExampleApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 15,OS=17.5' \
  build
```

Or open `ios-example/iosApp/LevelDBExampleApp.xcodeproj` in Xcode and Run on a simulator.

## Build device frameworks (optional)

```sh
./gradlew :ios-example:linkDebugFrameworkIosArm64
```

Note: the app is wired for UIScene and includes the required `CADisableMinimumFrameDurationOnPhone` plist entry for Compose.
