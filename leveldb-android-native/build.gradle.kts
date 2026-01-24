@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    id("signing")
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
            path = file("$rootDir/native/CMakeLists.txt")
            version = libs.versions.cmakeVersion.get()
        }
    }

    packaging {
        jniLibs.keepDebugSymbols += "**/*.so"
    }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}


dependencies {
    testImplementation(libs.test.junit)
}

signing {
    val signingKey = providers.gradleProperty("signingInMemoryKey").orNull
    val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword").orNull
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    } else {
        useGpgCmd()
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = false)

    signAllPublications()

    pom {
        val thisPom = this
        name.set(project.name)
        url.set("https://github.com/edwardstock/leveldb-multiplatform")
        description.set("Android JNI bindings for LevelDB")
        inceptionYear.set("2021")
        scm {
            connection.set("scm:git:${thisPom.url.get()}.git")
            developerConnection.set(connection)
            url = thisPom.url
        }
        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/edwardstock/leveldb-multiplatform/blob/master/LICENSE")
                distribution.set("repo")
            }
            license {
                name.set("BSD-3 Clause license")
                distribution.set("source")
            }
            license {
                name.set("Apache 2.0 license")
                url.set("https://github.com/edwardstock/leveldb-multiplatform/blob/master/third_party/stojan/LICENSE")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("edwardstock")
                name.set("Eduard Maximovich")
                email.set("edward.vstock@gmail.com")
                roles.add("forker")
                timezone.set("Europe/Mardid")
            }
            developer {
                id.set("hf")
                name.set("Stojan Dimitrovski")
                roles.add("owner")
            }
        }
    }
}
