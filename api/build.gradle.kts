import java.util.Properties

plugins {
    id("java")
    `java-library`
    id("eu.kakde.gradle.sonatype-maven-central-publisher") version "1.0.6"
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}


val publishProps = Properties()
val publishPropsFile = rootProject.file("publish.properties")

if (publishPropsFile.exists()) {
    publishPropsFile.inputStream().use(publishProps::load)
}

ext["signing.keyId"] = publishProps["signing.keyId"] ?: ""
ext["signing.password"] = publishProps["signing.password"] ?: ""
ext["signing.secretKeyRingFile"] = publishProps["signing.secretKeyRingFile"] ?: ""

val sonatypeUsername = publishProps["nexusUsername"] as? String ?: ""

val sonatypePassword = publishProps["nexusPassword"] as? String ?: ""

java {
    withSourcesJar()
    withJavadocJar()
}

object Meta {
    const val GROUP = "io.github.fassionfruit"
    const val ARTIFACT_ID = "api"
    const val VERSION = "0.5.0"

    const val DESC = "FassionFruit Java FFM binding API"
    const val LICENSE = "Apache License, Version 2.0"
    const val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0.txt"

    const val GITHUB_REPO = "FassionFruit/fruit"
}

sonatypeCentralPublishExtension {
    groupId.set(Meta.GROUP)
    artifactId.set(Meta.ARTIFACT_ID)
    version.set(Meta.VERSION)

    componentType.set("java")
    publishingType.set("USER_MANAGED")

    shaAlgorithms.set(listOf("SHA-256", "SHA-512"))

    username.set(
        System.getenv("SONATYPE_USERNAME") ?: sonatypeUsername
    )

    password.set(
        System.getenv("SONATYPE_PASSWORD") ?: sonatypePassword
    )

    pom {
        name.set("FassionFruit Core")
        description.set(Meta.DESC)
        url.set("https://github.com/${Meta.GITHUB_REPO}")

        licenses {
            license {
                name.set(Meta.LICENSE)
                url.set(Meta.LICENSE_URL)
            }
        }

        developers {
            developer {
                id.set("fassionfruit")
                name.set("FassionFruit")
                url.set("https://github.com/FassionFruit")
            }
        }

        scm {
            url.set("https://github.com/${Meta.GITHUB_REPO}")
            connection.set(
                "scm:git:https://github.com/${Meta.GITHUB_REPO}.git"
            )
            developerConnection.set(
                "scm:git:ssh://github.com/${Meta.GITHUB_REPO}.git"
            )
        }

        issueManagement {
            system.set("GitHub")
            url.set(
                "https://github.com/${Meta.GITHUB_REPO}/issues"
            )
        }
    }
}

