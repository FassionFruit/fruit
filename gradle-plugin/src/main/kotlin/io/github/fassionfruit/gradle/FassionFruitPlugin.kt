
package io.github.fassionfruit.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.TaskTriggersConfig

class FassionFruitPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "fassionfruit",
            FassionFruitExtension::class.java,
            project.objects,
            project
        )

        val generatedBindingsDirectory =
            project.layout.buildDirectory.dir(
                "generated/sources/fassionfruit/main/java"
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

            sourceFiles.from(extension.bindingSourceDirectory)
            metadataFiles.from(extension.native.outputDirectory)
            outputDirectory.set(generatedBindingsDirectory)
        }

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            project.tasks.matching {
                it.name == "compileKotlin"
            }.configureEach {
                dependsOn(generateBindings)
            }
        }

        project.plugins.withId("java") {
            val sourceSets = project.extensions.getByType(
                SourceSetContainer::class.java
            )

            val generatedSources = generateBindings.flatMap {
                it.outputDirectory
            }

            sourceSets.named("main") {
                java.srcDir(generatedSources)
            }

            project.tasks.named(
                "compileJava",
                JavaCompile::class.java
            ).configure {
                dependsOn(generateBindings)
            }

            project.tasks.named(
                "compileTestJava",
                JavaCompile::class.java
            ).configure {
                dependsOn(generateBindings)
            }

            project.tasks.matching {
                it.name == "sourcesJar"
            }.configureEach {
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

        val rootProject = project.rootProject

        rootProject.pluginManager.apply("org.jetbrains.gradle.plugin.idea-ext")

        val ideaModel = rootProject.extensions.getByType(IdeaModel::class.java)

        val projectSettings = (ideaModel.project as ExtensionAware)
            .extensions.getByName("settings") as ProjectSettings

        val taskTriggers = (projectSettings as ExtensionAware)
            .extensions.getByName("taskTriggers") as TaskTriggersConfig

        taskTriggers.afterSync(generateBindings.get())
    }
}
