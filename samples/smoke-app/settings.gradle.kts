pluginManagement {
    val magicAndroidPlatformRepositoryPath = providers
        .gradleProperty("magicAndroidPlatformRepositoryPath")
        .orNull
    val magicAndroidPlatformRepositoryUrl = providers
        .gradleProperty("magicAndroidPlatformRepositoryUrl")
        .orNull
    val magicAndroidPlatformVersion = providers
        .gradleProperty("magicAndroidPlatformVersion")
        .getOrElse("1.0.0")

    check(
        listOfNotNull(
            magicAndroidPlatformRepositoryPath,
            magicAndroidPlatformRepositoryUrl,
        ).size <= 1
    ) {
        "Use either magicAndroidPlatformRepositoryPath or magicAndroidPlatformRepositoryUrl, not both."
    }

    if (magicAndroidPlatformRepositoryPath == null && magicAndroidPlatformRepositoryUrl == null) {
        includeBuild("../..")
    }

    repositories {
        if (magicAndroidPlatformRepositoryPath != null) {
            maven {
                name = "magicAndroidPlatformVerification"
                url = uri(magicAndroidPlatformRepositoryPath)
            }
        }
        if (magicAndroidPlatformRepositoryUrl != null) {
            maven {
                name = "magicAndroidPlatformPublic"
                url = uri(magicAndroidPlatformRepositoryUrl)
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
