plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.librebuds"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.librebuds"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
