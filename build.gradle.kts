plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.core) apply false

    alias(libs.plugins.kotlin.multiplatform) apply  false
    alias(libs.plugins.kotlin.atomicfu) apply  false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.kover) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ktjni) apply false
}

val libVersion = "2.0.0"
group = "com.edwardstock.leveldb"
version = if (providers.gradleProperty("snapshot").isPresent) "$libVersion-SNAPSHOT" else libVersion
