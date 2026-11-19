plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = rootProject.group
version = rootProject.version

gradlePlugin {
    plugins {
        create("fassionfruit") {
            id = "io.github.fassionfruit"
            implementationClass = "io.github.fassionfruit.gradle.FassionFruitPlugin"
            displayName = "FassionFruit Gradle Plugin"
            description = "Builds and packages native libraries for FassionFruit applications."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}