package io.github.fassionfruit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

abstract class NativeBuildTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val buildDirectory: DirectoryProperty

    @get:Input
    abstract val buildSystem: Property<NativeBuildSystem>

    @get:Input
    abstract val profile: Property<String>

    @get:Input
    abstract val command: Property<String>

    @get:Input
    abstract val arguments: ListProperty<String>

    @get:Input
    abstract val environment: MapProperty<String, String>

    @get:Input
    abstract val target: Property<String>

    @TaskAction
    fun buildNative() {
        val source = sourceDirectory.get().asFile

        if (!source.isDirectory) {
            logger.lifecycle("Native source directory not found: $source")
            return
        }

        val output = outputDirectory.get().asFile
        val build = buildDirectory.get().asFile

        output.deleteRecursively()
        output.mkdirs()
        build.mkdirs()

        when (resolveBuildSystem(source)) {
            NativeBuildSystem.CMAKE -> buildWithCMake(source, build, output)
            NativeBuildSystem.CARGO -> buildWithCargo(source, output)
            NativeBuildSystem.MAKE -> buildWithMake(source, output)
            NativeBuildSystem.CUSTOM -> buildWithCustomCommand(source, output)
            NativeBuildSystem.AUTO -> error("Unresolved build system")
        }
    }

    private fun resolveBuildSystem(source: File): NativeBuildSystem {
        val configured = buildSystem.get()

        if (configured != NativeBuildSystem.AUTO) {
            return configured
        }

        if (File(source, "CMakeLists.txt").isFile) {
            return NativeBuildSystem.CMAKE
        }

        if (File(source, "Cargo.toml").isFile) {
            return NativeBuildSystem.CARGO
        }

        if (
            File(source, "Makefile").isFile ||
            File(source, "makefile").isFile
        ) {
            return NativeBuildSystem.MAKE
        }

        if (command.get().isNotBlank()) {
            return NativeBuildSystem.CUSTOM
        }

        throw GradleException(
            "Cannot detect native build system in $source"
        )
    }

    private fun buildWithCMake(
        source: File,
        build: File,
        output: File
    ) {
        if (!File(source, "CMakeLists.txt").isFile) {
            throw GradleException("CMakeLists.txt not found")
        }

        val outputPath = output.absolutePath
        val config = profile.get().uppercase()

        execute(
            source,
            listOf(
                "cmake",
                "-S", source.absolutePath,
                "-B", build.absolutePath,
                "-DCMAKE_BUILD_TYPE=${profile.get()}",
                "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=$outputPath",
                "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=$outputPath",
                "-DCMAKE_ARCHIVE_OUTPUT_DIRECTORY=$outputPath",
                "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_$config=$outputPath",
                "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_$config=$outputPath",
                "-DCMAKE_ARCHIVE_OUTPUT_DIRECTORY_$config=$outputPath"
            )
        )

        val args = mutableListOf(
            "cmake",
            "--build", build.absolutePath,
            "--config", profile.get()
        )

        if (target.get().isNotBlank()) {
            args.addAll(listOf("--target", target.get()))
        }

        args.addAll(arguments.get())

        execute(source, args)
    }

    private fun buildWithCargo(
        source: File,
        output: File
    ) {
        if (!File(source, "Cargo.toml").isFile) {
            throw GradleException("Cargo.toml not found")
        }

        val env = environment.get().toMutableMap()
        env["CARGO_TARGET_DIR"] = output.absolutePath

        val args = mutableListOf("cargo", "build")

        when (profile.get().lowercase()) {
            "release" -> args.add("--release")
            "debug" -> Unit
            else -> args.addAll(
                listOf("--profile", profile.get().lowercase())
            )
        }

        if (target.get().isNotBlank()) {
            args.addAll(listOf("--target", target.get()))
        }

        args.addAll(arguments.get())

        execute(source, args, env)
    }

    private fun buildWithMake(
        source: File,
        output: File
    ) {
        val args = mutableListOf("make")

        if (target.get().isNotBlank()) {
            args.add(target.get())
        }

        args.addAll(arguments.get())

        val env = buildEnvironment(output)
        execute(source, args, env)
    }

    private fun buildWithCustomCommand(
        source: File,
        output: File
    ) {
        val executable = command.get().trim()

        if (executable.isEmpty()) {
            throw GradleException("Custom build command is empty")
        }

        execute(
            source,
            listOf(executable) + arguments.get(),
            buildEnvironment(output)
        )
    }

    private fun buildEnvironment(output: File): Map<String, String> {
        return environment.get().toMutableMap().apply {
            put("FASSIONFRUIT_OUTPUT_DIR", output.absolutePath)
            put("FASSIONFRUIT_PROFILE", profile.get())
        }
    }

    private fun execute(
        workingDirectory: File,
        commandLine: List<String>,
        extraEnvironment: Map<String, String> = environment.get()
    ) {
        execOperations.exec {
            workingDir(workingDirectory)
            commandLine(commandLine)
            environment(
                "JAVA_HOME",
                System.getProperty("java.home")
            )
            environment(extraEnvironment)
        }
    }
}