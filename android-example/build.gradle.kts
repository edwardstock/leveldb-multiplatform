import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.core)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

group = rootProject.group
version = rootProject.version

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

android {
    namespace = "${group}.example"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 1
        versionName = version as String
    }

    buildFeatures {
        buildConfig = true
        viewBinding = false
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(projects.leveldb)

    implementation(libs.android.core.ktx)
    implementation(libs.android.lifecycle.runtime.ktx)
    implementation(libs.android.lifecycle.service)
    implementation(libs.android.compose.activity)

    implementation(platform(libs.android.compose.bom))
    implementation(libs.bundles.composeBom)

    implementation(libs.android.compose.activity)
    implementation(libs.android.compose.viewmodel)

    implementation(libs.di.hilt.core)
    implementation(libs.di.hilt.navigationCompose)
    ksp(libs.di.hilt.compiler)
    ksp(libs.di.hilt.daggerCompiler)
}
