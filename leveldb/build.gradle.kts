@file:Suppress("UnstableApiUsage")
@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget

fun KotlinNativeTarget.nativeLibDir(debug: Boolean = false): File {
    val prebuiltDir = "native/prebuilt"
    val arch = when (konanTarget.architecture) {
        Architecture.ARM64 -> "arm64"
        Architecture.X64 -> "x86_64"
        else -> error("Unsupported architecture for ${konanTarget.family}: ${konanTarget.architecture}")
    }
    val configDir = if (debug) "Debug" else "Release"

    return when (konanTarget.family) {
        Family.IOS -> {
            val osDir = when (konanTarget) {
                KonanTarget.IOS_X64,
                KonanTarget.IOS_SIMULATOR_ARM64 -> "ios-simulator"

                else -> "ios-device"
            }
            rootProject.file("$prebuiltDir/$osDir/$arch/$configDir")
        }

        Family.ANDROID -> rootProject.file("$prebuiltDir/android/$arch/$configDir")
        Family.MINGW -> rootProject.file("$prebuiltDir/windows/x86_64/$configDir")
        Family.OSX -> rootProject.file("$prebuiltDir/macos/$arch/$configDir")
        Family.LINUX -> rootProject.file("$prebuiltDir/linux/$arch/$configDir")

        else -> error("Unsupported platform: ${konanTarget.family}")
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kotlin.kover)
    alias(libs.plugins.ktjni)

    id("signing")
}

group = rootProject.group
version = rootProject.version

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvm {
        withSourcesJar(true)
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    linuxX64()
    linuxArm64()
    macosArm64()
    macosX64()
    mingwX64()

    android {
        minSdk { version = release(libs.versions.android.minSdk.get().toInt()) }
        compileSdk { version = release(libs.versions.android.compileSdk.get().toInt()) }
        namespace = group as String
        withSourcesJar(true)

        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    iosSimulatorArm64()
    iosArm64()

    applyHierarchyTemplate {
        //        withJvm()
        //        withLinuxX64()
        group("jvmShared") {
            withAndroidTarget()
            withJvm()
        }
        group("native") {
            withNative()
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.named("main").configure {
            cinterops.create("cleveldb") {
                defFile("$rootDir/native/cinterop/cleveldb.def")
                includeDirs("$rootDir/native/cinterop")
                val libDir = this@configureEach.nativeLibDir()
                extraOpts("-libraryPath", libDir.path)
            }
        }

        binaries.all {}
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlin.coroutines)
                implementation(libs.kotlin.atomicfu)
                implementation(libs.okio)
                implementation(libs.okio.fakeFileSystem)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.test.kotlin)
                implementation(libs.test.coroutines)
                implementation(kotlin("test"))
            }
        }

        nativeMain {
            dependsOn(commonMain.get())
        }
        nativeTest {
            dependsOn(commonTest.get())
        }

        val jvmSharedMain by getting
        val jvmSharedTest by getting

        androidMain {
            dependsOn(jvmSharedMain)
            dependencies {
                implementation(projects.leveldbAndroidNative)
                implementation(libs.okio)
                implementation(libs.kotlin.coroutines.android)
                implementation(libs.kotlin.atomicfu)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.kotlin.coroutines.jvm)
                implementation(libs.kotlin.atomicfu)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.test.junit)
            }
        }

        jvmSharedMain.apply {
            dependsOn(commonMain.get())
        }

        jvmSharedTest.apply {
            dependsOn(commonTest.get())
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.test.junit)
            }
        }
    }
}

atomicfu {
    transformJvm = false
}

val jniResourcesDir = layout.buildDirectory.dir("generated/jniResources/jvmMain")
val prepareJvmJniLibs = tasks.register<Sync>("prepareJvmJniLibs") {
    group = "build"
    description = "Stages the prebuilt JNI native libraries into the JVM resources for the runtime loader"
    val prebuiltDir = rootProject.layout.projectDirectory.dir("native/prebuilt")
    val jniLibs = listOf(
        "linux/x86_64/Release/libleveldb_jni.so" to "linux_64",
        "linux/arm64/Release/libleveldb_jni.so" to "linux_arm64",
        "macos/x86_64/Release/libleveldb_jni.dylib" to "osx_64",
        "macos/arm64/Release/libleveldb_jni.dylib" to "osx_arm64",
        "windows/x86_64/Release/leveldb_jni.dll" to "windows_64",
    )

    jniLibs.forEach { (sourcePath, archDir) ->
        from(prebuiltDir.file(sourcePath)) {
            into("natives/$archDir")
        }
    }

    into(jniResourcesDir)
}

// include prebuilt JNI libs in the JVM resources for the JVM loader
kotlin.sourceSets.named("jvmMain") {
    resources.srcDir(jniResourcesDir)
}

tasks.named<ProcessResources>("jvmProcessResources").configure {
    dependsOn(prepareJvmJniLibs)
    inputs.dir(jniResourcesDir)
}

tasks.matching { it.name in setOf("jvmJar", "jar") }.configureEach {
    dependsOn("jvmProcessResources")
}

tasks.withType<AbstractTestTask>().configureEach {
    dependsOn(prepareJvmJniLibs)
    reports.junitXml.required.set(false)
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    // Test doubles shipped in the public API (Mock/NoOp) — helpers for consumers'
                    // tests, not production code under test themselves.
                    "com.edwardstock.leveldb.test.*",
                    // Trivial exception types (message/cause plumbing, no real logic to cover).
                    "com.edwardstock.leveldb.exception.*",
                )
            }
        }
    }
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
    // Use the new Central Portal (Publisher API)
    publishToMavenCentral(automaticRelease = false)

    signAllPublications()

    pom {
        val thisPom = this
        name.set(project.name)
        url.set("https://github.com/edwardstock/leveldb-multiplatform")
        description.set("This is a Android wrapper for the amazing LevelDB")
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
