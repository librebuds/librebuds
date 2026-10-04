import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Short commit of the checkout, for the diagnostics export; "unknown" without git or outside a repository.
val buildCommit: String = runCatching {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrNull()?.takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) } ?: "unknown"

// Optional release signing, as in LibrePods. The properties file (RELEASE_STORE_FILE,
// RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD) comes from the Gradle property or
// environment variable LIBREBUDS_KEYSTORE_PROPERTIES (a relative path is taken from android/), else
// android/keystore.properties. Without it the release APK stays unsigned, so CI and contributors
// build without any key.
val keystorePropertiesFile: File = providers.gradleProperty("LIBREBUDS_KEYSTORE_PROPERTIES")
    .orElse(providers.environmentVariable("LIBREBUDS_KEYSTORE_PROPERTIES"))
    .map { rootProject.file(it) }
    .getOrElse(rootProject.file("keystore.properties"))
val keystoreProperties: Properties? = keystorePropertiesFile.takeIf { it.isFile }?.let { source ->
    Properties().apply { source.inputStream().use { load(it) } }
}

android {
    namespace = "io.github.librebuds"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.librebuds"
        minSdk = 33
        targetSdk = 37
        versionCode = 15
        versionName = "0.1.0-test.15"
        buildConfigField("String", "BUILD_COMMIT", "\"$buildCommit\"")
    }

    signingConfigs {
        keystoreProperties?.let { properties ->
            fun required(key: String): String =
                properties.getProperty(key) ?: error("$key is missing from $keystorePropertiesFile")
            create("release") {
                // A relative store path is taken relative to the properties file.
                storeFile = keystorePropertiesFile.parentFile.resolve(required("RELEASE_STORE_FILE"))
                storePassword = required("RELEASE_STORE_PASSWORD")
                keyAlias = required("RELEASE_KEY_ALIAS")
                keyPassword = required("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

// Bundles the repository's profiles/*.json as app assets, so ProfileAssets can load them at
// runtime without duplicating the files under android/app/src/main/assets.
abstract class CopyProfilesTask : Sync() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

val copyProfiles = tasks.register<CopyProfilesTask>("copyProfiles") {
    // outputDir is the assets source root; files land under its "profiles/" subdirectory so
    // they end up at assets/profiles/*.json rather than flattened at the assets root.
    from(rootDir.parentFile.resolve("profiles")) { include("*.json"); into("profiles") }
    outputDir.set(layout.buildDirectory.dir("generated/profileAssets"))
    into(outputDir)
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyProfiles) { it.outputDir }
    }
}

dependencies {
    implementation(project(":protocol"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.backdrop)
    implementation(libs.accompanist.permissions)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
