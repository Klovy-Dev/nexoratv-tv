pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Extension FFmpeg pré-compilée pour Media3 (AC3 / EAC3 / DTS…).
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "NexoraTV"
include(":app")
