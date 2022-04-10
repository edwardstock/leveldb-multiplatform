buildscript {
    repositories {
        mavenLocal()
        google()
        maven(url = uri("https://plugins.gradle.org/m2/"))
        maven(url = uri("https://jitpack.io"))
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${deps.versions.kotlin.base.get()}")
        classpath("com.android.tools.build:gradle:${deps.versions.agp.get()}")
        classpath("com.google.dagger:hilt-android-gradle-plugin:${deps.versions.hilt.base.get()}")
        classpath("com.edwardstock:cmakebuild:0.2.2")
    }
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven(url = uri("https://repo1.maven.org/maven2/"))
        maven(url = uri("https://clojars.org/repo/"))
        maven(url = uri("https://oss.sonatype.org/content/repositories/snapshots/"))
        maven(url = uri("https://jitpack.io"))
        maven(url = uri("https://oss.jfrog.org/libs-snapshot/"))
        maven(url = uri("https://oss.jfrog.org/artifactory/oss-snapshot-local/"))
    }
}

tasks.withType(Delete::class.java) {
    delete(rootProject.buildDir)
}

group = "com.edwardstock"
version = "1.0.1"