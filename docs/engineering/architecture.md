# Platform architecture

## Goal

The platform removes repeated build decisions from Android apps while keeping product behavior and
optional capabilities out of the baseline.

## Builds

The repository contains two Gradle builds:

- The root build produces and tests the convention plugin implementation.
- `samples/smoke-app` is an isolated Android consumer that resolves the root through a composite
  build. Its full app, application-only app, and Compose-only app fixtures form the
  consumer-boundary verification matrix for plugin IDs, optional capability combinations,
  transitive build dependencies, Android configuration, Pulse, Compose, and quality tasks.

`publicationCheck` publishes the implementation and all marker publications into an ignored,
build-local Maven repository. It verifies that the complete publication graph can be produced
without external credentials or changes to a developer's Maven Local cache.

The smoke app is not included as a root subproject. This prevents it from passing only because it
can see implementation projects directly.

## Plugin boundaries

### Android application

Owns shared Android application defaults:

- compile, minimum, and target SDK baselines
- Java compatibility
- release shrinking and optimization
- dependency metadata and packaging exclusions
- Android core and unit-test dependencies

It leaves namespace, application ID, version, signing identity, and product configuration to the
consumer's standard `android` block.

### Compose

Enables the Compose build feature and adds the shared Compose dependency baseline. It supports an
Android application or Android library and does not apply the application plugin.

### Pulse

Adds Pulse Android Compose and testing artifacts after an Android application or library plugin is
present. It does not force apps that use a different state container to adopt Pulse.

### Quality

Registers `magicQualityCheck` and attaches it to the consumer's `check` lifecycle. The task checks:

- Kotlin package and source-directory agreement
- `app -> feature -> domain -> core` dependency direction
- sibling feature isolation
- the `Contract / ViewModel / Screen` page skeleton
- production Kotlin files are limited to 400 lines
- string-resource key parity across declared locale directories

These checks form one non-configurable standard. Applying the Quality plugin means accepting every
rule; existing apps are refactored to comply instead of weakening the baseline.

## Future runtime artifacts

Runtime artifacts and a version BOM are introduced only when two real consumer apps share stable
semantics. They use one repository version and one release pipeline. Logical package boundaries do
not automatically become Maven artifacts.

## Publication boundary

`io.github.magic-xu:magic-android-platform-gradle-plugin` is the only implementation artifact in
the first release. Gradle also publishes one small marker POM per plugin ID so the plugins DSL can
resolve each capability. Those markers point to the same implementation JAR; they are not
separately maintained components.

All plugin IDs and future runtime artifacts use one repository version and one release operation.
Maven Central is the binary distribution boundary. The App Factory is a consumer that selects the
stable platform version for newly generated apps; it does not own or republish platform code.
