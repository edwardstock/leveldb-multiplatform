@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.core)
}

group = rootProject.group
version = rootProject.version

kotlin {
    iosArm64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries {
            executable {
                entryPoint = "com.edwardstock.leveldb.iosExample.main"
            }
            framework {
                baseName = "LevelDBExample"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.leveldb)
                implementation(libs.kotlin.coroutines)
                implementation(libs.okio)

                implementation(libs.compose.kmp.runtime)
                implementation(libs.compose.kmp.foundation)
                implementation(libs.compose.kmp.material3)
                implementation(libs.compose.kmp.ui)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
