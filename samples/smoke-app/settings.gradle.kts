pluginManagement {
    val magicAndroidPlatformRepositoryPath = providers
        .gradleProperty("magicAndroidPlatformRepositoryPath")
        .orNull
    val magicAndroidPlatformVersion = providers
        .gradleProperty("magicAndroidPlatformVersion")
        .getOrElse("0.1.0-SNAPSHOT")

    if (magicAndroidPlatformRepositoryPath == null) {
        includeBuild("../..")
    }

    repositories {
        if (magicAndroidPlatformRepositoryPath != null) {
            maven {
                name = "magicAndroidPlatformVerification"
                url = uri(magicAndroidPlatformRepositoryPath)
            }
        }
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

    plugins {
        id("io.github.magic-xu.magic-android-application") version magicAndroidPlatformVersion
        id("io.github.magic-xu.magic-android-compose") version magicAndroidPlatformVersion
        id("io.github.magic-xu.magic-android-pulse") version magicAndroidPlatformVersion
        id("io.github.magic-xu.magic-android-quality") version magicAndroidPlatformVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "magic-android-platform-smoke-app"
include(":app")
include(":application-only")
include(":compose-only")
