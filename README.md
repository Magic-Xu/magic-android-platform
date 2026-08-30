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
POMs. All plugin IDs share the same platform version and are published together.

The source belongs in the independent `magic-android-platform` Git repository. Released plugin
artifacts belong in Maven Central. During platform development, consumers use the composite build
shown below; the App Factory should pin a stable released version only after one non-Pulse app and
one Pulse app complete real migrations.

## Try the source build

Requirements:

- JDK 21 for Gradle
- Android SDK 36

Run the plugin tests and then compile the isolated consumer:

```bash
./gradlew platformCheck publicationCheck
./gradlew -p samples/smoke-app check assembleDebug
./gradlew -p samples/smoke-app \
  -PmagicAndroidPlatformRepositoryPath=../../build/publication-verification-repository \
  clean check assembleDebug
```

The Maven-mode Smoke App build consumes the generated plugin marker POMs and implementation JAR from
the isolated Maven repository. It must not resolve the platform through the composite source build.

## Use from a local consumer

Add the platform build to the consumer's `settings.gradle.kts`:

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

Apply only the capabilities the app needs:

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

After the first release, remove `includeBuild` and resolve the plugins from Maven Central with one
shared version:

```kotlin
plugins {
    id("io.github.magic-xu.magic-android-application") version "<platform-version>"
    id("io.github.magic-xu.magic-android-compose") version "<platform-version>"
    id("io.github.magic-xu.magic-android-pulse") version "<platform-version>"
    id("io.github.magic-xu.magic-android-quality") version "<platform-version>"
}
```

The application plugin supplies the shared SDK, Java, release, packaging, and base dependency
defaults. The Compose and Pulse plugins add their own dependencies. Quality rules are an atomic
standard: package paths, dependency direction, feature MVI skeletons, locale parity, and the
400-line production Kotlin limit are always enforced. Consumers cannot disable or relax individual
rules; an app that fails a rule must fix its architecture before the platform integration is
accepted.

See [architecture.md](docs/engineering/architecture.md) for ownership and dependency boundaries.
