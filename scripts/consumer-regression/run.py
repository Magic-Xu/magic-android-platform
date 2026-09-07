#!/usr/bin/env python3
"""Verify a Platform/Pulse candidate against pinned Factory and real Android apps."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys

sys.dont_write_bytecode = True

from evidence import (Evidence, VerificationError, commit, digest, git, instrumentation_count,
                      junit_results, load_matrix)

HERE = Path(__file__).resolve().parent
CENTRAL = "https://repo.maven.apache.org/maven2"
APP_TASKS = ["check", ":app:assembleDebug", ":app:assembleDebugAndroidTest",
             ":app:assembleRelease", ":app:bundleRelease", ":app:verifyPlatformConsumerResolution"]


def property_value(path: Path, name: str) -> str:
    match = re.search(r"^" + re.escape(name) + r"=(.+)$", path.read_text(), re.MULTILINE)
    if not match:
        raise VerificationError(f"Missing {name} in {path}")
    return match[1].strip()


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", type=Path, required=True, help="Platform Git checkout; defaults to its committed HEAD")
    parser.add_argument("--platform-ref", help="Explicit committed candidate ref; otherwise the source must be clean")
    parser.add_argument("--pulse", type=Path, help="Optional Pulse source candidate; runs mviReleaseCheck and stages all seven modules")
    parser.add_argument("--pulse-ref")
    parser.add_argument("--factory", type=Path, required=True)
    parser.add_argument("--snapmosaic", type=Path, required=True)
    parser.add_argument("--melonest", type=Path, required=True)
    parser.add_argument("--matrix", type=Path, default=HERE / "matrix.json")
    parser.add_argument("--output", type=Path, required=True, help="New private output directory; never reuses old results")
    parser.add_argument("--mode", choices=["candidate", "published"], default="candidate")
    parser.add_argument("--platform-version", help="Published-mode version override")
    parser.add_argument("--pulse-version", help="Explicit published Pulse version when no Pulse source is supplied")
    parser.add_argument("--device", help="Explicit adb serial; installs Debug APKs with -r and runs the pinned smoke classes")
    parser.add_argument("--adb", default="adb")
    return parser.parse_args()


def preflight(args, matrix):
    if args.mode == "published" and args.pulse:
        raise VerificationError("Published mode cannot consume a local Pulse candidate")
    if args.mode == "candidate" and args.platform_version:
        raise VerificationError("Candidate Platform version comes from its source; use published mode for a version override")
    if args.pulse_ref and not args.pulse:
        raise VerificationError("--pulse-ref requires --pulse")
    if args.pulse and args.pulse_version:
        raise VerificationError("Pulse source version must come from POM_VERSION_NAME")
    for name in ("platform", "pulse", "factory", "snapmosaic", "melonest"):
        path = getattr(args, name)
        if path:
            setattr(args, name, path.expanduser().resolve())
    args.output = args.output.expanduser().resolve()
    if args.output.exists():
        raise VerificationError("Output already exists; select a new directory to avoid stale evidence")
    for name in ("platform", "pulse"):
        source = getattr(args, name)
        if source and not getattr(args, name + "_ref") and git(source, "status", "--porcelain"):
            raise VerificationError(f"{name} source has uncommitted changes; commit it or select an explicit committed --{name}-ref")
    for name, config in [("factory", matrix["factory"]), *matrix["consumers"].items()]:
        commit(getattr(args, name), config["commit"])
    java = Path(os.environ.get("JAVA_HOME", "")) / "bin/java"
    output = subprocess.check_output([str(java), "-version"], stderr=subprocess.STDOUT, text=True)
    match = re.search(r'version "(\d+)', output)
    if not match or int(match[1]) < 21:
        raise VerificationError("Set JAVA_HOME to JDK 21 or newer")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk or not Path(sdk).is_dir():
        raise VerificationError("Set ANDROID_HOME to an installed Android SDK")
    if args.device:
        devices = subprocess.check_output([args.adb, "devices"], text=True)
        if not any(line.split()[:2] == [args.device, "device"] for line in devices.splitlines()):
            raise VerificationError("Requested adb device is not ready")


def gradle(evidence, stage, root, tasks, config=None, extra=()):
    command = ["./gradlew", "--console=plain", "--no-daemon"]
    if config:
        config = dict(config, resolutionDirectory=str(evidence.output / "resolution" / stage))
        config_path = evidence.output / (stage + "-config.json")
        config_path.write_text(json.dumps(config))
        command += ["--no-configuration-cache", "--init-script", str(HERE / "verify.init.gradle"),
                    "-Dplatform.consumer.config=" + str(config_path)]
    evidence.run(stage, command + list(extra) + tasks, root)
    if config:
        files = sorted(Path(config["resolutionDirectory"]).glob("*.json"))
        if not files:
            raise VerificationError(f"{stage} produced no dependency resolution evidence")
        evidence.data.setdefault("resolution", {})[stage] = [json.loads(p.read_text()) for p in files]
        evidence.save()


def build_factory(evidence, args, matrix, config):
    factory = evidence.snapshot("factory", args.factory, matrix["factory"]["commit"], matrix["factory"]["repository"])
    scripts = factory / "plugins/magic-app-dev/skills/android-app-factory/scripts"
    (evidence.output / "generated").mkdir()
    command = [sys.executable, str(scripts / "create_workspace.py"), "--app-name", "Consumer Regression",
               "--slug", "consumer-regression", "--application-id", "com.magic.consumerregression",
               "--github-owner", "Magic-Xu", "--product-sentence-en", "A generated verification application.",
               "--product-sentence-zh", "用于工程回归验证的生成应用。", "--parent-dir", str(evidence.output / "generated"),
               "--effective-date", "2026-09-08"]
    workspace = evidence.output / "generated/consumer-regression"
    plan_log = evidence.run("factory-plan", command + ["--dry-run"], factory)
    plan = json.loads(plan_log.read_text())
    if (plan.get("workspace") != str(workspace) or plan.get("remoteMutation") is not False
            or plan.get("androidRepository", {}).get("path") != str(workspace / "consumer-regression-android")
            or plan.get("legalRepository", {}).get("path") != str(workspace / "consumer-regression-legal")):
        raise VerificationError("Factory dry-run did not resolve the expected local workspace")
    evidence.run("factory-generate", command, factory)
    evidence.run("factory-structure", [sys.executable, str(scripts / "validate_workspace.py"),
                 "--workspace", str(workspace), "--skip-build"], factory)
    app = workspace / "consumer-regression-android"
    gradle(evidence, "factory-build", app, APP_TASKS, config)
    evidence.data["consumers"]["factory"] = {
        "unitTests": junit_results(app / "app/build/test-results/testDebugUnitTest"),
        "generatedCommit": git(app, "rev-parse", "HEAD"),
        "declaredPlatformVersion": json.loads((app / ".app-factory/spec.json").read_text())["platform"]["version"],
    }
    if git(app, "status", "--porcelain"):
        raise VerificationError("Generated Factory repository changed during verification")
    evidence.save()


def apk_identity(args, apk):
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    candidates = sorted((sdk / "build-tools").glob("*/aapt"), reverse=True)
    if not candidates:
        raise VerificationError("Android build-tools aapt is required to verify APK identity")
    text = subprocess.check_output([str(candidates[0]), "dump", "badging", str(apk)], text=True)
    match = re.search(r"^package: name='([^']+)'", text, re.MULTILINE)
    if not match:
        raise VerificationError("Cannot establish APK package identity")
    return match[1]


def run_device(evidence, args, name, root, consumer):
    app = root / "app/build/outputs/apk/debug/app-debug.apk"
    test = root / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    application_id = consumer["applicationId"]
    if apk_identity(args, app) != application_id or apk_identity(args, test) != application_id + ".test":
        raise VerificationError(f"Unexpected APK identities for {name}")
    adb = [args.adb, "-s", args.device]
    for label, apk in [("app", app), ("test", test)]:
        evidence.run(name + "-install-" + label, adb + ["install", "-r", "-t", str(apk)], root)
    classes = ",".join(consumer["deviceClasses"])
    evidence.run(name + "-stop", adb + ["shell", "am", "force-stop", application_id], root)
    log = evidence.run(name + "-device", adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class", classes,
                       application_id + ".test/androidx.test.runner.AndroidJUnitRunner"], root, timeout=900)
    count = instrumentation_count(log.read_text())
    evidence.data["consumers"][name]["deviceTests"] = {"tests": count, "classes": consumer["deviceClasses"]}
    evidence.save()


def build_consumer(evidence, args, name, consumer, config):
    source = getattr(args, name)
    root = evidence.snapshot(name, source, consumer["commit"], consumer["repository"])
    linked = evidence.local_config(source, root, consumer["localConfig"])
    evidence.data["consumers"][name] = {"localConfigFiles": linked}
    for index, command in enumerate(consumer["checks"]):
        evidence.run(f"{name}-check-{index + 1}", command, root)
    gradle(evidence, name + "-build", root, APP_TASKS, config)
    evidence.data["consumers"][name]["unitTests"] = junit_results(root / "app/build/test-results/testDebugUnitTest")
    artifacts = sorted(p for p in (root / "app/build/outputs").rglob("*") if p.suffix in (".apk", ".aab"))
    if not any(p.suffix == ".aab" for p in artifacts) or not any("release" in p.parts and p.suffix == ".apk" for p in artifacts):
        raise VerificationError(f"Missing Release artifacts for {name}")
    evidence.data["consumers"][name]["artifacts"] = [
        {"path": str(p), "sha256": digest(p), "bytes": p.stat().st_size} for p in artifacts]
    evidence.save()
    if args.device:
        run_device(evidence, args, name, root, consumer)
    if git(root, "diff", "HEAD", "--"):
        raise VerificationError(f"Tracked consumer sources changed during verification: {name}")
    evidence.data["consumers"][name]["trackedSourceUnchanged"] = True
    evidence.save()


def verify(args, matrix, evidence):
    platform = evidence.snapshot("platform", args.platform, args.platform_ref or "HEAD", "Magic-Xu/magic-android-platform")
    platform_version = args.platform_version or property_value(platform / "gradle.properties", "VERSION_NAME")
    dependencies = (platform / "gradle-plugin/src/main/kotlin/com/magic/platform/gradle/PlatformDependencies.kt").read_text()
    pulse_versions = re.findall(r'io.github.magic-xu:mvi-(?:platform-android-compose|platform-android-testing):([^"\s]+)', dependencies)
    if len(pulse_versions) != 2 or len(set(pulse_versions)) != 1:
        raise VerificationError("Platform production and testing Pulse declarations must agree")
    pulse_version = args.pulse_version or pulse_versions[0]
    pulse_repository = CENTRAL
    if args.pulse:
        pulse = evidence.snapshot("pulse", args.pulse, args.pulse_ref or "HEAD", "Magic-Xu/pulse")
        pulse_version = property_value(pulse / "gradle.properties", "POM_VERSION_NAME")
        gradle(evidence, "pulse-release", pulse, ["mviReleaseCheck"])
        pulse_repository = (pulse / "build/staging-repo").as_uri()
    config = dict(platformVersion=platform_version, pulseVersion=pulse_version, pulseRepository=pulse_repository)
    evidence.data.update(platformVersion=platform_version, pulseVersion=pulse_version,
                         platformDeclaredPulse=pulse_versions[0], matrixSha256=digest(args.matrix),
                         runnerSha256=digest(Path(__file__)), initScriptSha256=digest(HERE / "verify.init.gradle"))
    evidence.save()
    smoke = platform / "samples/smoke-app"
    smoke_tasks = ["check", "assembleDebug", "verifyPlatformConsumerResolution"]
    if args.mode == "candidate":
        gradle(evidence, "platform-release", platform, ["releaseCheck"])
        repository = platform / "build/publication-verification-repository"
        config["platformRepository"] = repository.as_uri()
        gradle(evidence, "smoke-source", smoke, smoke_tasks, dict(config, compositeSmoke=True))
        gradle(evidence, "smoke-staged", smoke, ["clean", *smoke_tasks], config,
               ["-PmagicAndroidPlatformRepositoryPath=" + str(repository), "-PmagicAndroidPlatformVersion=" + platform_version])
    else:
        config["platformRepository"] = CENTRAL
        gradle(evidence, "smoke-public", smoke, smoke_tasks, config,
               ["-PmagicAndroidPlatformRepositoryUrl=" + CENTRAL, "-PmagicAndroidPlatformVersion=" + platform_version])
    build_factory(evidence, args, matrix, config)
    if args.device:
        adb = [args.adb, "-s", args.device, "shell", "getprop"]
        evidence.data["device"] = {"status": "running", "serial": args.device,
            "model": subprocess.check_output(adb + ["ro.product.model"], text=True).strip(),
            "sdk": subprocess.check_output(adb + ["ro.build.version.sdk"], text=True).strip()}
    for name, consumer in matrix["consumers"].items():
        build_consumer(evidence, args, name, consumer, config)
    if args.device:
        evidence.data["device"]["status"] = "passed_selected_classes"


def main():
    evidence = None
    try:
        args = parse_args()
        matrix = load_matrix(args.matrix)
        preflight(args, matrix)
        evidence = Evidence(args.output, args.mode)
        verify(args, matrix, evidence)
        evidence.finish("passed_selected_gates")
        print(f"Verified; report: {args.output / 'summary.md'}")
        return 0
    except (Exception, KeyboardInterrupt) as error:
        if evidence:
            if evidence.data["device"]["status"] == "running":
                evidence.data["device"]["status"] = "incomplete"
            evidence.finish("failed", str(error) or "Interrupted")
        print(f"Consumer regression failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
