plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
