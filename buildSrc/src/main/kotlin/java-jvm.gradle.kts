package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
