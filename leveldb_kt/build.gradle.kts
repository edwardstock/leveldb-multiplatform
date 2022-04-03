import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    kotlin("jvm")
    id("maven-publish")
    id("signing")
    id("com.edwardstock.cmakebuild")
}

group = rootProject.group
version = rootProject.version

val localProps = gradleLocalProperties(rootDir)

sourceSets {
    getByName("main") {
        java.srcDir("src/main/java")
    }
    getByName("test") {
        java.srcDir("src/test/java")
    }
}

cmakeBuild {
    path = rootProject.file("native")

    windows {
        arguments += "-DJAVA_HOME=${localProps.getProperty("jdk.home").replace("\\", "\\\\")}"
    }

}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    group = "publishing"
    from(sourceSets.getByName("main").allSource)
}

tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    dependsOn("javadoc")
    from(tasks.findByName("javadoc"))
    group = "publishing"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.withType<Jar> {
    if (archiveClassifier.get() != "sources" && archiveClassifier.get() != "javadoc") {
        println("CLASSIFIER: ${archiveClassifier.get()}")
        dependsOn("buildCMake")
        from(file("${project.buildDir}/.cxx"))
    }
}

tasks.withType<Test> {
    dependsOn("buildCMake")
    val arch = when (System.getProperty("os.arch")) {
        "amd64" -> "x86_64"
        else -> System.getProperty("os.arch")
    }

    allJvmArgs = if (cmakeBuild.isWindows) {
        allJvmArgs + listOf(
            "-Djava.library.path=${project.buildDir}/.cxx/${arch}/${cmakeBuild.buildType}"
        )
    } else {
        allJvmArgs + listOf(
            "-Djava.library.path=${project.buildDir}/.cxx/${arch}/"
        )
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}


publishing {
    publications {
        create<MavenPublication>("release") {
            from(components.getByName("kotlin"))
            groupId = project.group as String
            artifactId = "leveldb-kt"
            version = project.version as String

            artifact(tasks.findByName("sourcesJar"))
            artifact(tasks.findByName("javadocJar"))

            pom {
                name.set(project.name)
                url.set("https://github.com/edwardstock/leveldb-multiplatform")
                description.set("This is a Kotlin wrapper for the amazing LevelDB")
                inceptionYear.set("2021")
                scm {
                    connection.set("scm:git:${pom.url}.git")
                    developerConnection.set(connection)
                    url.set(pom.url)
                }
                licenses {
                    license {
                        name.set("BSD license")
                        url.set("https://github.com/edwardstock/leveldb-multiplatform/blob/master/LICENSE")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("edwardstock")
                        name.set("Eduard Maximovich")
                        email.set("edward.vstock@gmail.com")
                        roles.add("forker")
                        timezone.set("Europe/Moscow")
                    }
                    developer {
                        id.set("hf")
                        name.set("Stojan Dimitrovski")
                        roles.add("owner")
                    }
                }
            }
        }
    }

    repositories {
        mavenLocal()
    }
}

project.tasks.withType<PublishToMavenLocal> {
    dependsOn("publishAllPublicationsToMavenLocalRepository")
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}
