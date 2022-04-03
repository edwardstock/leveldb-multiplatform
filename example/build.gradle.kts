plugins {
    id("com.android.application")
    id("kotlin-android")
    kotlin("kapt")
    id("dagger.hilt.android.plugin")
}

group = rootProject.group
version = rootProject.version

android {
    buildToolsVersion = deps.versions.buildTools.get()
    compileSdk = deps.versions.maxSdk.get().toInt()

    defaultConfig {
        minSdk = deps.versions.minSdk.get().toInt()
        targetSdk = deps.versions.maxSdk.get().toInt()
        versionCode = 1
        versionName = version as String
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    compileOnly("javax.annotation:jsr250-api:1.0")
    compileOnly("javax.inject:javax.inject:1")

    implementation(project(":leveldb-android"))

    implementation(deps.base.android.core)
    implementation(deps.base.android.core)
    implementation(deps.base.android.annotations)
    implementation(deps.base.android.appcompat)
    implementation(deps.base.android.appcompatResources)
    implementation(deps.base.android.recyclerview)
    implementation(deps.base.android.material)
    implementation(deps.base.android.lifecycle.viewmodel)
    implementation(deps.base.android.lifecycle.runtime)
    kapt(deps.base.android.lifecycle.compiler)
    implementation(deps.base.android.ktx.activity)
    implementation(deps.base.android.ktx.fragment)

    implementation(deps.base.kotlin.coroutines)

    implementation(deps.base.hilt.core)
    kapt(deps.base.hilt.compiler)


}


