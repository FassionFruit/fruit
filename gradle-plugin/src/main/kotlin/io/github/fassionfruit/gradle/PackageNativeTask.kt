
package io.github.fassionfruit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

abstract class PackageNativeTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty

    @get:Input
    abstract val targetTriple: Property<String>

    @get:Input
    abstract val targetOs: Property<String>

    @get:Input
    abstract val targetArchitecture: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun packageNativeLibraries() {
        val inputRoot = inputDirectory.get().asFile.toPath()
            .toAbsolutePath()
            .normalize()

        val outputRoot = outputDirectory.get().asFile.toPath()
            .toAbsolutePath()
            .normalize()

        if (!Files.isDirectory(inputRoot)) {
            throw GradleException(
                "Native build output directory does not exist: $inputRoot"
            )
        }

        val (os, architecture) = if (targetTriple.get().isBlank() &&
            targetOs.get().isNotBlank() && targetArchitecture.get().isNotBlank()) {
            normalizePlatform(targetOs.get(), targetArchitecture.get())
        } else {
            detectPlatform(targetTriple.get())
        }
        val platform = "$os-$architecture"

        val libraries = Files.walk(inputRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { isNativeLibrary(it) }
                .toList()
        }

        if (libraries.isEmpty()) {
            throw GradleException(
                """
                No native libraries found.

                Searched directory:
                $inputRoot

                Supported extensions:
                - Windows: .dll
                - Linux: .so, .so.*
                - macOS: .dylib

                Check your native build configuration and make sure
                the build produces a shared library.
                """.trimIndent()
            )
        }

        val duplicates = libraries
            .groupBy { it.fileName.toString() }
            .filterValues { it.size > 1 }

        if (duplicates.isNotEmpty()) {
            throw GradleException(
                "Duplicate native library names: ${
                    duplicates.keys.joinToString()
                }"
            )
        }

        if (Files.exists(outputRoot)) {
            outputRoot.toFile().deleteRecursively()
        }

        val platformDirectory = outputRoot.resolve("native").resolve(platform)
        val metadataDirectory = outputRoot.resolve("metadata")

        Files.createDirectories(platformDirectory)
        Files.createDirectories(metadataDirectory)

        libraries.forEach { library ->
            val destination = platformDirectory.resolve(
                library.fileName
            )

            Files.copy(
                library,
                destination,
                StandardCopyOption.REPLACE_EXISTING
            )

            logger.lifecycle(
                "FassionFruit: packaged ${library.fileName}"
            )
        }

        val metadataFiles = Files.walk(inputRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".ffm.json", true) }
                .toList()
        }
        val duplicateMetadata = metadataFiles.groupBy { it.fileName.toString() }
            .filterValues { it.size > 1 }
        if (duplicateMetadata.isNotEmpty()) {
            throw GradleException(
                "Duplicate FassionFruit metadata files: ${duplicateMetadata.keys.joinToString()}"
            )
        }
        metadataFiles.forEach { metadata ->
            Files.copy(
                metadata,
                metadataDirectory.resolve(metadata.fileName),
                StandardCopyOption.REPLACE_EXISTING
            )
            logger.lifecycle("FassionFruit: packaged metadata ${metadata.fileName}")
        }

        logger.lifecycle(
            "FassionFruit: packaged ${libraries.size} native libraries and ${metadataFiles.size} metadata file(s) for $platform"
        )
    }

    private fun isNativeLibrary(path: Path): Boolean {
        val name = path.fileName.toString().lowercase()

        return when {
            name.endsWith(".dll") -> true
            name.endsWith(".dylib") -> true
            name.endsWith(".so") -> true
            Regex(""".*\.so\..+""").matches(name) -> true
            else -> false
        }
    }

    private fun normalizePlatform(osValue: String, architectureValue: String): Pair<String, String> {
        val normalizedOs = osValue.trim().lowercase()
        val os = when {
            normalizedOs.contains("mac") || normalizedOs.contains("darwin") -> "macos"
            normalizedOs.contains("win") -> "windows"
            normalizedOs.contains("linux") -> "linux"
            else -> throw GradleException("Unsupported native target OS: $osValue")
        }
        val architecture = when (architectureValue.trim().lowercase()) {
            "amd64", "x86_64", "x64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            "i386", "i486", "i586", "i686", "x86" -> "x86"
            "arm", "arm32", "armv7" -> "arm"
            "riscv64" -> "riscv64"
            else -> throw GradleException("Unsupported native target architecture: $architectureValue")
        }
        return os to architecture
    }

    private fun detectPlatform(target: String): Pair<String, String> {
        val triple = target.trim().lowercase()

        val os = if (triple.isBlank()) {
            System.getProperty("os.name").lowercase()
        } else {
            triple
        }

        val architecture = if (triple.isBlank()) {
            System.getProperty("os.arch").lowercase()
        } else {
            triple.substringBefore("-")
        }

        val normalizedOs = when {
            os.contains("windows") || os.contains("mingw") ||
                    os.contains("msvc") -> "windows"

            os.contains("darwin") || os.contains("apple") ||
                    os.contains("macos") -> "macos"

            os.contains("linux") -> "linux"

            else -> throw GradleException(
                "Unsupported native target OS: $os"
            )
        }

        val normalizedArchitecture = when (architecture) {
            "amd64", "x86_64", "x64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            "i386", "i486", "i586", "i686", "x86" -> "x86"
            "arm", "arm32", "armv7" -> "arm"
            "riscv64" -> "riscv64"

            else -> throw GradleException(
                "Unsupported native target architecture: $architecture"
            )
        }

        return normalizedOs to normalizedArchitecture
    }
}
