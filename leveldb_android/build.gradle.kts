/*
 * Stojan Dimitrovski
 *
 * Copyright (c) 2019, Stojan Dimitrovski <sdimitrovski@gmail.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OFz SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    kotlin("plugin.parcelize")
    id("maven-publish")
    id("signing")
}

group = rootProject.group
version = rootProject.version

android {
    buildToolsVersion = deps.versions.buildTools.get()
    compileSdk = deps.versions.maxSdk.get().toInt()

    defaultConfig {
        minSdk = deps.versions.minSdk.get().toInt()
        targetSdk = deps.versions.maxSdk.get().toInt()

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++14"
                this.arguments
            }
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(
                "src/main/java",
                // use sources directly to make fat aar
                "../leveldb_kt/src/main/java"
            )
        }
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    testOptions {
        animationsDisabled = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    externalNativeBuild {
        cmake {
            path("${rootProject.projectDir}/native/CMakeLists.txt")
            version = "3.18.1"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(deps.base.android.core)
    implementation(deps.base.kotlin.coroutines)

    androidTestImplementation(deps.test.core)
    androidTestImplementation(deps.test.junit)
    androidTestImplementation(deps.test.rules)
    androidTestImplementation(deps.test.runner)

    testImplementation(deps.test.junit)
    testImplementation(deps.test.core)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }
            groupId = project.group as String
            artifactId = "leveldb-android"
            version = project.version as String

            pom {
                name.set(project.name)
                url.set("https://github.com/edwardstock/leveldb-multiplatform")
                description.set("This is a Android wrapper for the amazing LevelDB")
                inceptionYear.set("2021")
                scm {
                    connection.set("scm:git:${pom.url}.git")
                    developerConnection.set(connection)
                    url.set(pom.url)
                }
                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://github.com/edwardstock/leveldb-multiplatform/blob/master/LICENSE")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("edwardstock")
                        name.set("Eduard Maximovich")
                        email.set("edward.vstock@gmail.com")
                        roles.add("owner")
                        timezone.set("Europe/Moscow")
                    }
                }
            }
        }
    }

    repositories {
        mavenLocal()
        if (hasProperty("ossrhUsername") && hasProperty("ossrhPassword")) {
            maven(url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")) {
                credentials.username = findProperty("ossrhUsername") as String?
                credentials.password = findProperty("ossrhPassword") as String?
            }
        }
    }
}

project.tasks.withType<PublishToMavenLocal> {
    dependsOn("publishAllPublicationsToMavenLocalRepository")
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

