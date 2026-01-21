@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.library)
}

group = rootProject.group
version = rootProject.version

android {
    namespace = "${group}.android"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }
    ndkVersion = libs.versions.ndkVersion.get()

    defaultConfig {
        minSdk {
            version = release(libs.versions.android.minSdk.get().toInt())
        }

        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
                arguments += listOf(
                    // todo: fix this when leveldb bump min required cmake version
                    "-DCMAKE_WARN_DEPRECATED=OFF",
                    "-DLEVELDB_BUILD_BENCHMARKS=OFF",
                    "-DLEVELDB_BUILD_TESTS=OFF",
                    "-DLEVELDB_INSTALL=OFF",
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("debug") {
            externalNativeBuild {
                cmake {
                    // extra debug flags
                }
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            externalNativeBuild {
                cmake {
                    // extra release flags
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            // Point to your CMakeLists for Android build
            // Typical place for KMP is under androidMain
            path = file("$rootDir/native/CMakeLists.txt")
            version = libs.versions.cmakeVersion.get() // optional; otherwise AGP uses installed CMake
        }
    }

    // Make sure jni libs and debug symbols are packaged as you want
    packaging {
        // Keep .so debug symbols in release AAR (useful for crash symbolication)
        jniLibs.keepDebugSymbols += "**/*.so"
        // If you bundle c++_shared, you may need:
        // jniLibs.useLegacyPackaging = true // AGP <8; usually not needed now
    }

    // Optional per-variant overrides
    //    androidComponents {
    //        onVariants(selector().all()) { variant ->
    //            // e.g., add a per-variant CMake define:
    //            // variant.androidTest?.externalNativeBuild?.cmake?.arguments?.add("-DSOME_FLAG=1")
    //        }
    //    }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}


dependencies {
    testImplementation(libs.test.junit)
}
