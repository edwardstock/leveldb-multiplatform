@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/") // Snapshot versions
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "leveldb-multiplaform"
include(":leveldb")
include(":android-example")
include(":leveldb-android-native")
include(":ios-example")
