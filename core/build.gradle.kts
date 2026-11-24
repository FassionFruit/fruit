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

    dependsOn(buildNativeTestLibraries)
}

val nativeTestSources = fileTree("../native") {
    include("*.c")
}

val buildNativeTestLibraries = nativeTestSources.files.map { source ->
    tasks.register<Exec>("buildNativeTest${source.nameWithoutExtension.replaceFirstChar { it.uppercase() }}") {
        val output = source.resolveSibling("${source.nameWithoutExtension}.so")

        inputs.file(source)
        outputs.file(output)

        commandLine(
            "gcc",
            "-shared",
            "-fPIC",
            source,
            "-o",
            output.absolutePath
        )
    }
}