plugins {
    id("buildsrc.convention.java-jvm")
}

dependencies {
    api(project(":api"))

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")

    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}
