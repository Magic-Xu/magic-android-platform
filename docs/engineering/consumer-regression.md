# Verify Platform and Pulse against real consumers

Run `scripts/consumer-regression/run.py` before promoting a Platform/Pulse candidate. It builds
isolated, committed snapshots of Platform, optional Pulse source, the Android App Factory,
SnapMosaic, and MeloNest. [matrix.json](../../scripts/consumer-regression/matrix.json) pins the
accepted Factory and app commits and the device test classes. Local app edits are left in place.

## Run a candidate

Requirements: Python 3.10+, Git, JDK 21 through `JAVA_HOME`, an Android SDK through `ANDROID_HOME`,
and access to the pinned commits in each local repository. Dependency downloads require network
access. Real consumers use their existing ignored local configuration for Firebase and signing:
SnapMosaic requires `local.properties` and `app/google-services.json`; MeloNest may also use
`local.gradle`. Keep signing file paths absolute. The runner links these files into private clones
without recording their contents in the report. Existing app identity, release signing and
shrinking settings remain active.

Commit the candidate first. By default, dirty Platform/Pulse source checkouts are rejected;
`--platform-ref` and `--pulse-ref` explicitly select committed refs when unrelated edits exist.
The output directory must be new. Keep it outside repositories or under an ignored build directory.

```bash
python3 scripts/consumer-regression/run.py \
  --platform /path/to/magic-android-platform \
  --pulse /path/to/pulse \
  --factory /path/to/app-dev-skills \
  --snapmosaic /path/to/SnapMosaic \
  --melonest /path/to/MeloNest \
  --device DEVICE_SERIAL \
  --output /private/path/to/new-candidate-run
```

Omit `--pulse` to test Platform with its declared public Pulse version. With `--pulse`, the runner
runs Pulse's `mviReleaseCheck` and consumes all required Pulse artifacts from that run's staging
repository. It records both Platform's declared Pulse version and the candidate version actually
resolved, so compatibility experiments do not imply that Platform's default has been upgraded.
Use `--adb /path/to/adb` if adb is not on `PATH`.

`--device` installs the two apps' Debug and test APKs with `adb install -r -t` and runs the pinned
classes on that serial. It stops the app before instrumentation. It does not uninstall apps or
clear their data; tests can exercise and alter app state, so use a development device. A signing
mismatch fails the run. Omitting the device leaves coverage explicitly `not_requested`.
Some OEM installers require device confirmation even for adb installation. Record that provisioning
assistance separately from the app tests; use a device that permits unattended installation when
running without an operator. The runner does not change device security settings.

## What a successful run proves

| Stage | Gate |
| --- | --- |
| Pulse source, when supplied | Existing release, API, compatibility, publication and stress gates |
| Platform candidate | `releaseCheck`, including regression tool tests and publication shape |
| Smoke | Source and staged Maven builds; Application-only, Compose-only and full combinations |
| Fresh Factory workspace | Canonical generator and structure validator; full build gates below |
| SnapMosaic and MeloNest | Pinned repository checks; full build gates below |
| Selected device tests | SnapMosaic editor save/input/lifecycle, commerce and shared-image handoff; MeloNest Create lifecycle and root routing |

SnapMosaic's editor lifecycle checks cover pending input across image replacement/clear and
cancellation when the ViewModel owner closes.

Every generated or real app runs `check`, Debug APK, Debug test APK, Release APK and Release AAB
builds. The Factory structure validator runs with `--skip-build` because the runner executes its
four canonical Gradle gates itself, adding test APK compilation and dependency verification.
Google Services and signing checks stay enabled. Crashlytics mapping upload tasks are disabled for
local regression; local Release shrinking and mapping still run.

An external Gradle init script selects the candidate's four plugin markers and all seven Pulse
module coordinates. Exclusive repositories prevent fallback to a public artifact of the same
version. The verifier checks actual Debug/Release/unit-test/instrumentation dependency versions,
Android testing dependencies, and staged artifact SHA-256 values. Application-only and Compose-only
Smoke modules must remain free of Pulse. Candidate injection does not edit consumer build files or
promote the Factory's declared default. The source Smoke uses composite substitution; the other
consumers verify the staged implementation JAR.

`summary.md` gives the result and log links; `result.json` records commits, source trees, runner and
matrix hashes, resolved artifacts, test totals, APK/AAB hashes and device coverage. New output
directories prevent old test results from satisfying a run. Failed commands, missing/all-skipped
JUnit results, zero-test instrumentation and runner failures produce a nonzero exit status.

Reports and clones stay local in a directory created with owner-only access. They contain private
source, configuration links and potentially sensitive app logs; do not commit or upload the output
directory to Platform's public repository. Retain it while reviewing the candidate, then remove it
through the normal local cleanup process.

## Public release and baseline promotion

After publication, rerun the same command with `--mode published`, omitting `--pulse`. Platform and
Pulse resolve exclusively from Maven Central; no local candidate release gate is run in this mode.
The default versions come from the supplied Platform commit. Optional `--platform-version` and
`--pulse-version` select an explicit public pair. The report records those resolved versions and
the local source commit used for the Smoke fixture separately.

Promote the Factory default in its own repository only after the public pair passes. After an
accepted app migration, update that app's full commit in `matrix.json` and rerun the affected
baseline. Include existing product gates and select device classes according to the changed
behavior. Do not automatically follow app `HEAD` or introduce product rules into plugin code.

Public Platform CI runs the regression tool's unit tests through `releaseCheck`, plus the existing
Smoke builds. The private real-app command is a maintainer release gate and must be reviewed before
tagging; it is not automatically enforced by the public tag workflow. Store upload, full app UI
acceptance, real payment flows, sustained-load qualification, and Pulse's managed-device suite
remain separate checks when the change requires them. These selected tests do not replace them.
