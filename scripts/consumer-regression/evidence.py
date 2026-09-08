"""Local-only execution evidence for pinned consumer verification."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time
import xml.etree.ElementTree as ET


class VerificationError(RuntimeError):
    pass


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def git(path: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(path), *args], text=True).strip()


def commit(path: Path, ref: str) -> str:
    return git(path, "rev-parse", "--verify", "--end-of-options", ref + "^{commit}")


def repository_name(path: Path) -> str:
    url = git(path, "remote", "get-url", "origin")
    match = re.fullmatch(r"(?:git@github\.com:|https://github\.com/)([^/]+/[^/]+?)(?:\.git)?", url)
    if not match:
        raise VerificationError("Expected a GitHub origin without embedded credentials")
    return match[1]


def load_matrix(path: Path) -> dict:
    matrix = json.loads(path.read_text())
    if (not isinstance(matrix, dict) or matrix.get("schemaVersion") != 1
            or not isinstance(matrix.get("consumers"), dict)
            or set(matrix["consumers"]) != {"snapmosaic", "melonest"}
            or not isinstance(matrix.get("factory"), dict)):
        raise VerificationError("Unsupported consumer matrix")
    for item in [matrix["factory"], *matrix["consumers"].values()]:
        if not isinstance(item, dict) or not re.fullmatch(r"[a-f0-9]{40}", str(item.get("commit", ""))):
            raise VerificationError("Matrix revisions must be full immutable commit IDs")
        if not re.fullmatch(r"[\w.-]+/[\w.-]+", str(item.get("repository", ""))):
            raise VerificationError("Matrix repository must be owner/name")
    for item in matrix["consumers"].values():
        if (not isinstance(item.get("localConfig"), list) or not isinstance(item.get("checks"), list)
                or not isinstance(item.get("deviceClasses"), list) or not item["deviceClasses"]
                or not re.fullmatch(r"[A-Za-z]\w*(?:\.[A-Za-z]\w*)+", str(item.get("applicationId", "")))):
            raise VerificationError("Incomplete consumer configuration")
        if any(not isinstance(command, list) or not command or
               any(not isinstance(arg, str) or not arg for arg in command) for command in item["checks"]):
            raise VerificationError("Checks must be nonempty argument lists")
        if any(not isinstance(name, str) or not re.fullmatch(r"[\w.]+", name) for name in item["deviceClasses"]):
            raise VerificationError("Device classes must be fully qualified class names")
        for name in item.get("localConfig", []):
            if not isinstance(name, str) or not name:
                raise VerificationError("Local config entries must be relative file paths")
            relative = Path(name)
            if relative.is_absolute() or ".." in relative.parts or relative == Path("."):
                raise VerificationError("Local config entries must stay inside the consumer checkout")
    return matrix


def junit_results(directory: Path) -> dict:
    files = sorted(directory.glob("TEST-*.xml"))
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in files:
        root = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(root.attrib.get(key, 0))
    if not files or totals["tests"] <= totals["skipped"] or totals["failures"] or totals["errors"]:
        raise VerificationError(f"Missing or unsuccessful JUnit results: {directory}")
    return {**totals, "directory": str(directory)}


def instrumentation_count(output: str) -> int:
    if any(marker in output for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "INSTRUMENTATION_ABORTED")):
        raise VerificationError("Instrumentation reported failure despite adb's exit status")
    match = re.search(r"OK \((\d+) tests?\)", output)
    if not match or int(match[1]) == 0:
        raise VerificationError("Instrumentation did not prove a nonempty successful test run")
    return int(match[1])


class Evidence:
    def __init__(self, output: Path, mode: str):
        output.mkdir(parents=True, exist_ok=False, mode=0o700)
        self.output = output
        self.data = {"schemaVersion": 1, "mode": mode, "status": "running", "sources": {},
                     "stages": [], "consumers": {}, "device": {"status": "not_requested"}}
        self.save()

    def save(self):
        temporary = self.output / "result.json.tmp"
        temporary.write_text(json.dumps(self.data, indent=2) + "\n")
        temporary.replace(self.output / "result.json")

    def run(self, name: str, command: list[str], cwd: Path, *, env=None, timeout=1800) -> Path:
        log = self.output / "logs" / (name + ".log")
        log.parent.mkdir(exist_ok=True)
        stage = {"name": name, "command": [str(x) for x in command], "cwd": str(cwd),
                 "log": str(log), "status": "running"}
        self.data["stages"].append(stage)
        self.save()
        print(f"[{name}] running; log: {log}", flush=True)
        start = time.monotonic()
        with log.open("w") as stream:
            try:
                process = subprocess.Popen(command, cwd=cwd, env=env, stdout=stream,
                                           stderr=subprocess.STDOUT, start_new_session=True)
            except OSError:
                stage["status"] = "failed"
                self.save()
                raise
            try:
                code = process.wait(timeout=timeout)
            except (subprocess.TimeoutExpired, KeyboardInterrupt):
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                stage["status"] = "interrupted"
                stage["seconds"] = round(time.monotonic() - start, 2)
                self.save()
                raise
        stage.update(exitCode=code, seconds=round(time.monotonic() - start, 2),
                     status="passed" if code == 0 else "failed")
        self.save()
        if code:
            raise VerificationError(f"{name} failed; inspect {log}")
        return log

    def snapshot(self, name: str, source: Path, revision: str, expected_repository: str) -> Path:
        if repository_name(source).lower() != expected_repository.lower():
            raise VerificationError(f"Wrong repository supplied for {name}")
        sha = commit(source, revision)
        destination = self.output / "sources" / name
        destination.parent.mkdir(exist_ok=True)
        self.run(name + "-clone", ["git", "clone", "--shared", "--no-checkout", str(source), str(destination)], self.output)
        self.run(name + "-checkout", ["git", "checkout", "--detach", sha], destination)
        self.data["sources"][name] = {"repository": expected_repository, "commit": sha,
            "tree": git(destination, "rev-parse", "HEAD^{tree}"), "path": str(destination)}
        self.save()
        return destination

    def local_config(self, source: Path, checkout: Path, names: list[str]) -> list[str]:
        linked = []
        for name in names:
            original, target = source / name, checkout / name
            if not original.is_file():
                continue
            if target.exists() or git(checkout, "ls-files", "--", name):
                raise VerificationError(f"Refusing to replace tracked consumer config: {name}")
            ignored = subprocess.run(["git", "check-ignore", "-q", "--", name], cwd=checkout)
            if ignored.returncode:
                raise VerificationError(f"Consumer config is not ignored: {name}")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.symlink_to(original.resolve())
            linked.append(name)
        return linked

    def finish(self, status: str, error: str | None = None):
        self.data["status"] = status
        if error:
            self.data["error"] = error
        self.save()
        lines = ["# Consumer regression", "", f"Result: **{status}**", "",
                 f"Platform: `{self.data.get('platformVersion', 'unknown')}`; "
                 f"Pulse: `{self.data.get('pulseVersion', 'unknown')}`.", "",
                 "| Source | Commit |", "| --- | --- |"]
        for name, source in self.data["sources"].items():
            lines.append(f"| {name} | `{source['commit']}` |")
        lines += ["", "| Stage | Result | Evidence |", "| --- | --- | --- |"]
        for stage in self.data["stages"]:
            lines.append(f"| {stage['name']} | {stage['status']} | [log]({stage['log']}) |")
        lines += ["", "Coverage: build and selected tests only. Device coverage: "
                  + self.data["device"]["status"] + ".", "",
                  "This is not store-release approval, sustained-load qualification, or proof of real payments.",
                  "Crashlytics mapping upload is disabled; local Release shrinking and mapping remain enabled.", "",
                  "Full provenance, dependency hashes, test totals and artifacts: [result.json](result.json).", ""]
        if error:
            lines += ["Failure: " + error, ""]
        (self.output / "summary.md").write_text("\n".join(lines))
