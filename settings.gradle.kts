enableFeaturePreview("VERSION_CATALOGS")

rootProject.name = "leveldb-multiplatform"


include(
    ":example",
    ":leveldb-kt",
    ":leveldb-android",
//    ":mdnsjni"
)

project(":leveldb-kt").projectDir = file("leveldb_kt")
project(":leveldb-android").projectDir = file("leveldb_android")
// plugin for build cmake in gradle

pluginManagement {

    repositories {
//        mavenLocal()
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

dependencyResolutionManagement {


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
    versionCatalogs {
        create("deps") {
            from(files("gradle/deps.versions.toml"))
        }
    }

}

