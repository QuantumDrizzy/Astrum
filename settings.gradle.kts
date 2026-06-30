pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ASTRUM"
include(":app")

// Shared astronomy spine (single source of truth for the ephemeris math). Composite build:
// no publishing, fully local; Gradle substitutes the com.quantumdrizzy:astro-core dependency
// with the sibling ../astro-core project on disk.
includeBuild("../astro-core")
