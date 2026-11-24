plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.2.1"
}


group = "io.github.fassionfruit"
version = "0.5.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    website = "https://github.com/FassionFruit/fruit"
    vcsUrl = "https://github.com/FassionFruit/fruit"

    plugins {
        create("fassionFruit") {
            id = "io.github.fassionfruit"
            implementationClass = "io.github.fassionfruit.gradle.FassionFruitPlugin"

            displayName = "FassionFruit Gradle Plugin"
            description = """
                Gradle plugin for building and packaging native libraries
                with FassionFruit.
            """.trimIndent()

            tags = listOf(
                "native",
                "java-ffm",
                "kotlin",
                "native-build"
            )
        }
    }
}

tasks.test {
    useJUnitPlatform()
}