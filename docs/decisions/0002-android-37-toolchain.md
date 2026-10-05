# Android 37 build toolchain

Status: implemented for the 1.3.0 candidate; not published as a new Platform release.

The application convention compiles against API 37 while retaining target API 36. The coordinated build uses AGP 9.4.1, Gradle 9.6.0 and the Compose BOM 2026.09.00. Kotlin remains 2.2.10, matching the Kotlin Gradle plugin dependency bundled by this AGP release. Java/Kotlin bytecode remains Java 17 and the Gradle daemon uses JDK 21.

Consumers should install Android SDK `platforms;android-37.0` and adopt the coordinated wrapper when updating the published Platform dependency. Apps remain responsible for their target SDK decision and physical-device compatibility evidence. Do not raise consumer target SDK merely because the compile SDK supports it. Compose and Pulse remain optional plugins; Profile generation and app-specific journeys belong to the consumer.

Update every explicit consumer Platform version together, including settings plugin management and the root version catalog. A consumer's explicit `compileSdk` overrides the convention default and must also move to API 37. Align separately declared Android plugins, such as `com.android.test`, with AGP 9.4.1. Compose callers that read the current locale should use `LocalLocale.current.platformLocale` with this BOM. Android 17 instrumentation needs AndroidX Test versions compatible with its input APIs; Espresso 3.7.0 and AndroidX Test JUnit 1.3.0 were used for the SnapMosaic migration.

Before publication, run `releaseCheck`, both source and staged-Maven smoke builds, and affected real consumer validation. The current candidate passes the three repository checks. A fresh Factory workspace and explicitly migrated SnapMosaic and MeloNest snapshots pass checks and Debug / instrumentation APK builds against staged 1.3.0 artifacts. These consumer runs produce test packages only; they do not satisfy the full Release APK / AAB matrix required for publication. Stable publication remains pending. Source validation through a temporary isolated Maven repository must not leave local repository paths in a consumer.

Gradle 9.6 requires the quality verification task to declare its cacheability explicitly. `MagicQualityTask` is non-cacheable because it has no output artifacts; mandatory checks and their failures remain unchanged.

[AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
