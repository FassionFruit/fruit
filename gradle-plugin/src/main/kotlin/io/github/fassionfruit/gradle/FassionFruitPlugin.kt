
package io.github.fassionfruit.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

private fun isAutoBindingDeclaration(file: File): Boolean {
    val source = file.readText()
    return Regex(
        """@NativeLibrary\s*\(\s*(?:value\s*=\s*)?"[^"]+"\s*\)\s*(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed)\s+)*class\s+[A-Za-z_$][A-Za-z0-9_$]*\s*\{\s*}"""
    ).containsMatchIn(source)
}

class FassionFruitPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "fassionfruit",
            FassionFruitExtension::class.java,
            project.objects,
            project
        )

        fun hasNativeProject(): Boolean {
            val source = extension.native.sourceDirectory.get().asFile

            return (source.isDirectory && source.walkTopDown().any { it.isFile }) ||
                extension.native.additionalSourceDirectories.files.any { directory ->
                    directory.isDirectory && directory.walkTopDown().any { it.isFile }
                }
        }

        val nativeBuild = project.tasks.register(
            "fassionfruitNativeBuild",
            NativeBuildTask::class.java
        ) {
            group = "fassionfruit"
            description = "Builds native libraries"

            sourceDirectory.set(extension.native.sourceDirectory)
            sourceFiles.from(
                extension.native.sourceDirectory,
                extension.native.additionalSourceDirectories
            )
            additionalSourceDirectories.from(extension.native.additionalSourceDirectories)

            outputDirectory.set(extension.native.outputDirectory)
            buildDirectory.set(extension.native.buildDirectory)

            buildSystem.set(extension.native.build.system)
            profile.set(extension.native.build.profile)
            command.set(extension.native.build.command)
            arguments.set(extension.native.build.arguments)
            clangArguments.set(extension.native.build.clangArguments)
            clangCommand.set(extension.native.build.clangCommand)
            environment.set(extension.native.build.environment)
            target.set(extension.native.build.target)

            onlyIf("Native project is not empty") {
                hasNativeProject()
            }
        }

        val packageNative = project.tasks.register(
            "fassionfruitPackageNative",
            PackageNativeTask::class.java
        ) {
            group = "fassionfruit"
            description = "Packages native libraries"

            dependsOn(nativeBuild)

            inputDirectory.set(extension.native.outputDirectory)
            targetTriple.set(extension.native.build.target)
            targetOs.set(extension.native.packaging.os)
            targetArchitecture.set(extension.native.packaging.architecture)

            outputDirectory.set(
                project.layout.buildDirectory.dir(
                    "generated/fassionfruit/native-resources"
                )
            )

            onlyIf("Native project is not empty") {
                hasNativeProject()
            }
        }

        val generateBindings = project.tasks.register(
            "fassionfruitGenerateBindings",
            GenerateNativeBindingsTask::class.java
        ) {
            group = "fassionfruit"
            description = "Generates static Java bindings from native metadata"
            dependsOn(nativeBuild)
            val mainSources = project.fileTree("src/main")
            mainSources.include("**/*.java", "**/*.kt")
            sourceFiles.from(mainSources)
            metadataFiles.from(extension.native.outputDirectory.map { directory ->
                project.fileTree(directory.asFile).apply {
                    include("**/*.ffm.json")
                }
            })
            outputDirectory.set(
                project.layout.buildDirectory.dir("generated/sources/fassionfruit/main/java")
            )
        }

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            project.tasks.matching { it.name == "compileKotlin" }.configureEach {
                dependsOn(generateBindings)
            }
        }

        project.plugins.withId("java") {
            val sourceSets = project.extensions.getByType(
                org.gradle.api.tasks.SourceSetContainer::class.java
            )
            val mainSourcePath = project.layout.projectDirectory.dir("src/main")
                .asFile.absoluteFile.toPath().normalize().toString()

            sourceSets.named("main") {
                java.srcDir(generateBindings.flatMap { it.outputDirectory })
                java.exclude { element ->
                    val file = element.file
                    val path = file.absoluteFile.toPath().normalize().toString()
                    path.startsWith(mainSourcePath) &&
                        file.extension == "java" && isAutoBindingDeclaration(file)
                }
            }

            project.tasks.named(
                "compileJava",
                org.gradle.api.tasks.compile.JavaCompile::class.java
            ).configure {
                dependsOn(generateBindings)
            }

            project.tasks.named(
                "processResources",
                ProcessResources::class.java
            ).configure {
                dependsOn(packageNative)

                from(packageNative.flatMap { it.outputDirectory }) {
                    into("META-INF/fassionfruit")
                }
            }
        }
    }
}
