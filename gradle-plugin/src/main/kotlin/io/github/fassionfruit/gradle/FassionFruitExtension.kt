package io.github.fassionfruit.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.Project
import javax.inject.Inject

open class FassionFruitExtension @Inject constructor(
    objects: ObjectFactory,
    project: Project
) {
    val native = objects.newInstance(
        NativeExtension::class.java,
        objects,
        project
    )
}

open class NativeExtension @Inject constructor(
    objects: ObjectFactory,
    project: Project
) {
    val sourceDirectory: DirectoryProperty =
        objects.directoryProperty()
            .convention(project.layout.projectDirectory.dir("native"))

    val outputDirectory: DirectoryProperty =
        objects.directoryProperty()
            .convention(project.layout.buildDirectory.dir("fassionfruit/native"))

    val buildDirectory: DirectoryProperty =
        objects.directoryProperty()
            .convention(project.layout.buildDirectory.dir("fassionfruit/native-build"))

    val build = objects.newInstance(
        NativeBuildOptions::class.java,
        objects
    )
}

open class NativeBuildOptions @Inject constructor(
    objects: ObjectFactory
) {
    val system: Property<NativeBuildSystem> =
        objects.property(NativeBuildSystem::class.java)
            .convention(NativeBuildSystem.AUTO)

    val profile: Property<String> =
        objects.property(String::class.java)
            .convention("Release")

    val command: Property<String> =
        objects.property(String::class.java)
            .convention("")

    val arguments: ListProperty<String> =
        objects.listProperty(String::class.java)
            .convention(emptyList())

    val environment: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java)
            .convention(emptyMap())

    val target: Property<String> =
        objects.property(String::class.java)
            .convention("")
}