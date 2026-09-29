plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Tests read shared data (profiles/, test-vectors/) from the repository root.
    systemProperty("librebuds.repoRoot", rootDir.parentFile.absolutePath)
    // Make Gradle aware tests read these directories, so editing the JSON re-runs tests.
    inputs.dir(rootDir.parentFile.resolve("profiles"))
    inputs.dir(rootDir.parentFile.resolve("test-vectors"))
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
