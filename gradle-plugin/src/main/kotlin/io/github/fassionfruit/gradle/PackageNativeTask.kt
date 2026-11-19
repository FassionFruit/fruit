package io.github.fassionfruit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption

abstract class PackageNativeTask : DefaultTask() {

    @get:Internal
    abstract val inputDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun packageNativeLibraries() {
        val inputRoot = inputDirectory.get().asFile.toPath()
        val outputRoot = outputDirectory.get().asFile.toPath()

        if (Files.exists(outputRoot)) {
            outputRoot.toFile().deleteRecursively()
        }

        Files.createDirectories(outputRoot)

        val libraries = nativeFiles.files.filter { it.isFile }

        libraries.forEach { library ->
            val source = library.toPath()

            val relative = if (source.startsWith(inputRoot)) {
                inputRoot.relativize(source)
            } else {
                source.fileName
            }

            val destination = outputRoot.resolve(relative)

            Files.createDirectories(destination.parent)

            Files.copy(
                source,
                destination,
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        logger.lifecycle(
            "FassionFruit: packaged ${libraries.size} native libraries"
        )
    }
}