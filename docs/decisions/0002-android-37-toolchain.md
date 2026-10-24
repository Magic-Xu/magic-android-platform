# Android 37 build toolchain

Status: released as Platform 1.3.0 on 2026-10-24.

The application convention compiles against API 37 while retaining target API 36. The coordinated build uses AGP 9.4.1, Gradle 9.6.0 and the Compose BOM 2026.09.00. Kotlin remains 2.2.10, matching the Kotlin Gradle plugin dependency bundled by this AGP release. Java/Kotlin bytecode remains Java 17 and the Gradle daemon uses JDK 21.

Consumers should install Android SDK `platforms;android-37.0` and adopt the coordinated wrapper when updating the published Platform dependency. Apps remain responsible for their target SDK decision and physical-device compatibility evidence. Do not raise consumer target SDK merely because the compile SDK supports it. Compose and Pulse remain optional plugins; Profile generation and app-specific journeys belong to the consumer.

Update every explicit consumer Platform version together, including settings plugin management and the root version catalog. A consumer's explicit `compileSdk` overrides the convention default and must also move to API 37. Align separately declared Android plugins, such as `com.android.test`, with AGP 9.4.1. Compose callers that read the current locale should use `LocalLocale.current.platformLocale` with this BOM. Android 17 instrumentation needs AndroidX Test versions compatible with its input APIs; Espresso 3.7.0 and AndroidX Test JUnit 1.3.0 were used for the SnapMosaic migration.

Platform 1.3.0 passed `releaseCheck`, signed publication verification, both source and staged-Maven Smoke matrices, and the full Factory, SnapMosaic and MeloNest build matrix, including Release APK and AAB. The real-app verification used SnapMosaic 2.7.0 and a committed, isolated build-tool migration of the matrix-pinned MeloNest source; it does not promote MeloNest or the Factory default. The [release workflow](https://github.com/Magic-Xu/magic-android-platform/actions/runs/40102632360) verifies public implementation and marker artifacts and consumption without local Platform sources.

Install the coordinated SDK and JDK, update the wrapper and plugin versions, and resolve Platform 1.3.0 from Maven Central. Remove temporary candidate-repository init scripts from normal consumer builds. Keep signing and product configuration in the consumer app.

Gradle 9.6 requires the quality verification task to declare its cacheability explicitly. `MagicQualityTask` is non-cacheable because it has no output artifacts; mandatory checks and their failures remain unchanged.

[AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
