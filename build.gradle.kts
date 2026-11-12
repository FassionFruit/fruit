import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create

rootProject.group = "io.github.fassionfruit"
rootProject.version = "0.4.1"

val publishedModules = setOf(":api", ":core")

subprojects {

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(25))
            }

            withSourcesJar()
        }

        if (path in publishedModules) {
            apply(plugin = "maven-publish")

            plugins.withId("maven-publish") {
                extensions.configure<PublishingExtension> {
                    publications {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                        }
                    }
                }
            }
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        docEncoding = "UTF-8"
        addStringOption("Xdoclint:all", "-quiet")
    }
}