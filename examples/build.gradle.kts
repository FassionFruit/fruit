import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("java")
}

group = "io.github.fassionfruit"
version = rootProject.version

repositories {
    mavenCentral()
    maven("https://jitpack.io/")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":core"))
}

val osName = System.getProperty("os.name").lowercase()
val libExtension = when {
    osName.contains("win") -> "dll"
    osName.contains("mac") -> "dylib"
    else -> "so"
}
val toolchainHome = javaToolchains.launcherFor(java.toolchain)
    .map { it.metadata.installationPath.asFile.absolutePath }

val cmakeConfigure = tasks.register<Exec>("cmakeConfigure") {
    description = ""
    workingDir = file("native")
    commandLine("cmake", "-S", ".", "-B", "build", "-DCMAKE_BUILD_TYPE=Release")
    doFirst {
        environment("JAVA_HOME", toolchainHome.get())
    }
}

val cmakeBuild = tasks.register<Exec>("cmakeBuild") {
    description = "Build the native example"
    dependsOn(cmakeConfigure)
    workingDir = file("native")
    commandLine("cmake", "--build", "build", "--config", "Release")
}

val cargoBuild = tasks.register<Exec>("cargoBuild") {
    description = "Builds the Rust native example."
    workingDir = file("native-rust")
    commandLine("cargo", "build", "--release")
}

val nativeLibraryName = when {
    osName.contains("win") -> "native_example.dll"
    osName.contains("mac") -> "native_example.dylib"
    else -> "native_example.so"
}

val rustLibraryName = when {
    osName.contains("win") -> "native_example_rust.dll"
    osName.contains("mac") -> "libnative_example_rust.dylib"
    else -> "libnative_example_rust.so"
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(cmakeBuild, cargoBuild)

    from(file("native/out/$nativeLibraryName")) {
        into("native")
    }

    from(file("native-rust/target/release/$rustLibraryName")) {
        into("native")
    }
}
