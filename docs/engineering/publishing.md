# Publish Magic Android Platform

## Release boundary

Maven Central is the only production binary repository. A platform version publishes one
implementation artifact:

```text
io.github.magic-xu:magic-android-platform-gradle-plugin:<version>
```

The four Gradle plugin marker POMs are generated from the same build and point to that implementation
artifact. A release also contains sources, Dokka javadocs, Gradle module metadata, complete POM
metadata, and GPG signatures.

Released Maven Central versions are immutable. Fixes use a new patch version; never move a release
tag or attempt to overwrite an existing version.

## Credentials

The `io.github.magic-xu` namespace is already verified by the public Pulse releases. Platform
publishing does not register another namespace.

GitHub Actions requires these repository secrets:

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `SIGNING_IN_MEMORY_KEY`
- `SIGNING_IN_MEMORY_KEY_PASSWORD`

Use a dedicated Central Portal token for this repository so it can be rotated or revoked without
interrupting Pulse publishing. The GPG key represents the same publisher identity and may be shared
with Pulse. A platform code or artifact defect does not affect Pulse's already published coordinates.
However, a leaked or revoked shared GPG private key requires both repositories to rotate their
signing credentials. Never commit either credential.

## Candidate gate

The initial public release is `1.0.0`, matching the minimum version accepted by the Android App
Factory. Before tagging it:

1. Set `VERSION_NAME=1.0.0` in `gradle.properties`.
2. Run the complete local release gate:

   ```bash
   ./gradlew clean releaseCheck --stacktrace
   ./gradlew -p samples/smoke-app clean check assembleDebug
   ./gradlew -p samples/smoke-app \
     -PmagicAndroidPlatformRepositoryPath=../../build/publication-verification-repository \
     -PmagicAndroidPlatformVersion=1.0.0 \
     clean check assembleDebug
   ```

3. Require a clean worktree and successful CI for the exact release commit.
4. Create the annotated tag `v1.0.0` on that exact commit.

Do not run the remote publish task manually. The guarded annotated-tag
`.github/workflows/publish-maven-central.yml` workflow verifies the tag and version, reruns every
release gate, checks signed candidate files, publishes through the Central Portal, waits for all
implementation and marker artifacts to become public, and finally rebuilds the Smoke App without
local platform sources.

For a later release, update `VERSION_NAME`, pass the same candidate gates, and create the matching
annotated semantic-version tag. The workflow rejects tags that do not exactly match the source
version.
