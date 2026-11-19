package io.github.fassionfruit.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.language.jvm.tasks.ProcessResources

class FassionFruitPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "fassionfruit",
            FassionFruitExtension::class.java,
            project.objects,
            project
        )

        val nativeBuild = project.tasks.register(
            "fassionfruitNativeBuild",
            NativeBuildTask::class.java
        ) {
            group = "fassionfruit"
            description = "Builds native libraries"

            sourceDirectory.set(extension.native.sourceDirectory)
            sourceFiles.from(extension.native.sourceDirectory)
            outputDirectory.set(extension.native.outputDirectory)
            buildDirectory.set(extension.native.buildDirectory)

            buildSystem.set(extension.native.build.system)
            profile.set(extension.native.build.profile)
            command.set(extension.native.build.command)
            arguments.set(extension.native.build.arguments)
            environment.set(extension.native.build.environment)
            target.set(extension.native.build.target)
        }

        val packageNative = project.tasks.register(
            "fassionfruitPackageNative",
            PackageNativeTask::class.java
        ) {
            group = "fassionfruit"
            description = "Packages native libraries"

            dependsOn(nativeBuild)

            inputDirectory.set(extension.native.outputDirectory)

            nativeFiles.from(
                project.fileTree(extension.native.outputDirectory) {
                    include(
                        "**/*.dll",
                        "**/*.so",
                        "**/*.so.*",
                        "**/*.dylib"
                    )
                }
            )

            outputDirectory.set(
                project.layout.buildDirectory.dir(
                    "generated/fassionfruit/native-resources"
                )
            )
        }

        project.plugins.withId("java") {
            project.tasks.named(
                "processResources",
                ProcessResources::class.java
            ).configure {
                dependsOn(packageNative)

                from(packageNative.flatMap { task ->
                    task.outputDirectory
                }) {
                    into("META-INF/fassionfruit/native")
                }
            }
        }
    }
}