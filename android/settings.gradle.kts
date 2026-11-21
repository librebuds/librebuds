pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "librebuds"
include(":protocol")
include(":app")

 // Vendor protocol library remains in a pinned GPLv3 git submodule.
 // Composite build avoids copying/forking PairFree's 17 drivers and model tables.
includeBuild("../third_party/PairFree/android") {
    dependencySubstitution {
        substitute(module("io.github.omegadestroyer:pairfree-core")).using(project(":core"))
        substitute(module("io.github.omegadestroyer:pairfree-protocol")).using(project(":protocol"))
    }
}
