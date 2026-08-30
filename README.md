# Magic Android Platform

Shared Gradle conventions and quality gates for Magic Android apps.

The platform keeps Android, Compose, Pulse, and repository checks independently selectable. Product
code continues to use the standard Android Gradle DSL for identity and version information.

The cross-session source of truth is
[platform-blueprint.md](docs/engineering/platform-blueprint.md). Read it before changing scope,
selecting a consumer app, updating the Factory, or preparing a release.

## Release shape

The four plugin IDs are capabilities from one release unit, not four independently versioned
components. A release produces one implementation JAR plus four lightweight Gradle plugin marker
POMs. The implementation also publishes sources, Dokka javadocs, Gradle module metadata, complete
POM metadata, and signatures. All plugin IDs share the same platform version and are published
together.

The source belongs in the independent `magic-android-platform` Git repository. Released plugin
artifacts belong in Maven Central. Version `1.0.0` is the current stable release and the Android App
Factory's tested default.

## Use the released platform

Configure both plugin and dependency repositories in `settings.gradle.kts`:

```kotlin
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
```

Use one platform version for every declared capability. The complete Factory baseline declares all
four in the root build; a consumer that does not need a capability may omit its line:

```kotlin
// Root build.gradle.kts
plugins {
    id("io.github.magic-xu.magic-android-application") version "1.0.0" apply false
    id("io.github.magic-xu.magic-android-compose") version "1.0.0" apply false
    id("io.github.magic-xu.magic-android-pulse") version "1.0.0" apply false
    id("io.github.magic-xu.magic-android-quality") version "1.0.0" apply false
}
```

```kotlin
// App module build.gradle.kts
plugins {
    id("io.github.magic-xu.magic-android-application")
    id("io.github.magic-xu.magic-android-compose")
    id("io.github.magic-xu.magic-android-pulse")
    id("io.github.magic-xu.magic-android-quality")
}

android {
    namespace = "com.example.app"
    defaultConfig {
        applicationId = "com.example.app"
        versionCode = 1
        versionName = "1.0.0"
    }
}
```

The application plugin supplies the shared SDK, Java, release, packaging, and base dependency
defaults. The Compose and Pulse plugins add their own dependencies. Quality rules are an atomic
standard: package paths, dependency direction, feature-UI platform boundaries, feature MVI
skeletons, locale parity, and the 400-line production Kotlin limit are always enforced. Consumers
cannot disable or relax individual rules; an app that fails a rule must fix its architecture before
the platform integration is accepted.

## Try the source build

Requirements:

- JDK 21 for Gradle
- Android SDK 36

Run the plugin tests and then compile the isolated consumer:

```bash
./gradlew releaseCheck
./gradlew -p samples/smoke-app check assembleDebug
./gradlew -p samples/smoke-app \
  -PmagicAndroidPlatformRepositoryPath=../../build/publication-verification-repository \
  clean check assembleDebug
```

The Maven-mode Smoke App build consumes the generated plugin marker POMs and implementation JAR from
the isolated Maven repository. It must not resolve the platform through the composite source build.

## Develop platform changes with a local consumer

Composite builds are for developing an unreleased Platform change against a real consumer. Add the
local platform build to the consumer's `settings.gradle.kts`; never persist a machine-specific path
in Factory output or a released consumer branch:

```kotlin
pluginManagement {
    includeBuild("../magic-android-platform")
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
```

The consumer may then apply only the capabilities under development without declaring a published
version:

```kotlin
plugins {
    id("io.github.magic-xu.magic-android-application")
    id("io.github.magic-xu.magic-android-compose")
    id("io.github.magic-xu.magic-android-pulse")
    id("io.github.magic-xu.magic-android-quality")
}

android {
    namespace = "com.example.app"
    defaultConfig {
        applicationId = "com.example.app"
        versionCode = 1
        versionName = "1.0.0"
    }
}
```

Declare Android app-specific build plugins, such as Google Services or Firebase Crashlytics, in the
same module plugin block even when they are conditionally applied later. Do not keep them only in a
root `plugins { ... apply false }` block: older transitive build dependencies in that parent plugin
scope can shadow the platform's AGP and bundletool dependencies.

See [architecture.md](docs/engineering/architecture.md) for ownership and dependency boundaries,
and [publishing.md](docs/engineering/publishing.md) for the guarded release process.
