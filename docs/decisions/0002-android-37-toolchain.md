# Android 37 build toolchain

Status: implemented for the 1.3.0 candidate; not published as a new Platform release.

The application convention compiles against API 37 while retaining target API 36. The coordinated build uses AGP 9.4.1, Gradle 9.6.0 and the Compose BOM 2026.09.00. Kotlin remains 2.2.10, matching the Kotlin Gradle plugin dependency bundled by this AGP release. Java/Kotlin bytecode remains Java 17 and the Gradle daemon uses JDK 21.

Consumers should install Android SDK `platforms;android-37.0` and adopt the coordinated wrapper when updating the published Platform dependency. Apps remain responsible for their target SDK decision and physical-device compatibility evidence. Do not raise consumer target SDK merely because the compile SDK supports it. Compose and Pulse remain optional plugins; Profile generation and app-specific journeys belong to the consumer.

Before publication, run `releaseCheck`, both source and staged-Maven smoke builds, and affected real consumer validation. The current candidate passes the three repository checks and SnapMosaic compilation; stable publication and the full consumer release matrix are separate pending release work. Source validation through a temporary isolated Maven repository must not leave local repository paths in a consumer.

Gradle 9.6 requires the quality verification task to declare its cacheability explicitly. `MagicQualityTask` is non-cacheable because it has no output artifacts; mandatory checks and their failures remain unchanged.

[AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
