# Magic Android Platform Collaboration Rules

## Purpose

Magic Android Platform standardizes the build and quality baseline shared by Magic Android apps.
It is a product-line platform for Android projects, not a replacement for AndroidX, Pulse, or
product-specific architecture.

## Repository boundaries

- Gradle convention plugins live in `gradle-plugin/`.
- Executable consumer fixtures live in `samples/` and are independent Gradle builds.
- Durable engineering decisions live in `docs/decisions/`.
- Platform behavior must not depend on any consumer app's product model.
- Compose and Pulse remain opt-in plugins. The application baseline must not require either one.
- Do not add runtime libraries until at least two real consumer apps need the same semantics.

## Plugin responsibilities

- `magic-android-application`: Android application defaults only.
- `magic-android-compose`: Compose build feature and Compose dependencies.
- `magic-android-pulse`: Pulse Android and testing dependencies.
- `magic-android-quality`: mandatory repository quality checks with no per-rule exemptions.

Plugins may depend on upstream Android and Kotlin build APIs. Product IDs, signing secrets, ad IDs,
legal copy, application resources, navigation, and feature behavior remain in consumer apps.

## Changes

- Never edit on `main` or `master`; create or continue a task branch.
- Keep the four plugins independently applicable and covered by tests.
- Prefer standard Android DSL over a second wrapper DSL unless a repeated invariant cannot be
  expressed safely with conventions.
- Do not publish an empty BOM or placeholder runtime artifact.
- Public API changes require a migration note before the first stable release.

## Validation

Before delivery run:

```bash
./gradlew releaseCheck
./gradlew -p samples/smoke-app check assembleDebug
./gradlew -p samples/smoke-app \
  -PmagicAndroidPlatformRepositoryPath=../../build/publication-verification-repository \
  clean check assembleDebug
```

Also confirm production Kotlin files remain below 800 lines and the working tree contains no build
outputs, local SDK paths, credentials, or product-specific artifacts.
