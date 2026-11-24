import io.github.fassionfruit.gradle.NativeBuildSystem

plugins {
    id("java")
    id("io.github.fassionfruit")
}

group = "io.github.fassionfruit"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

fassionfruit {
    native {
        sourceDirectory.set(layout.projectDirectory.dir("native"))
        additionalSourceDirectories.from(layout.projectDirectory.dir("native-rust"))

        build {
            system.set(NativeBuildSystem.AUTO)
            profile.set("Release")
        }
    }
}
