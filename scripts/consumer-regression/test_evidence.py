import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from evidence import (Evidence, VerificationError, git, instrumentation_count,
                      junit_results, load_matrix)


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def repository(self):
        root = self.root / "repository"
        root.mkdir()
        git(root, "init", "--initial-branch=main")
        git(root, "config", "user.name", "Regression Test")
        git(root, "config", "user.email", "test@example.com")
        git(root, "remote", "add", "origin", "https://github.com/example/consumer.git")
        (root / "source.txt").write_text("accepted\n")
        (root / ".gitignore").write_text("local.properties\n")
        git(root, "add", ".")
        git(root, "commit", "-m", "Fixture")
        return root

    def test_snapshot_uses_pinned_commit_and_preserves_dirty_source(self):
        source = self.repository()
        accepted = git(source, "rev-parse", "HEAD")
        (source / "source.txt").write_text("later\n")
        git(source, "commit", "-am", "Later commit")
        (source / "source.txt").write_text("uncommitted\n")
        evidence = Evidence(self.root / "output", "candidate")
        checkout = evidence.snapshot("app", source, accepted, "example/consumer")
        self.assertEqual((checkout / "source.txt").read_text(), "accepted\n")
        self.assertEqual((source / "source.txt").read_text(), "uncommitted\n")
        self.assertEqual(evidence.data["sources"]["app"]["commit"], accepted)

    def test_wrong_repository_rejected_before_clone(self):
        source = self.repository()
        evidence = Evidence(self.root / "output", "candidate")
        with self.assertRaises(VerificationError):
            evidence.snapshot("app", source, "HEAD", "another/consumer")
        self.assertFalse((evidence.output / "sources").exists())

    def test_private_config_is_ignored_and_not_copied_into_git(self):
        source = self.repository()
        evidence = Evidence(self.root / "output", "candidate")
        checkout = evidence.snapshot("app", source, "HEAD", "example/consumer")
        (source / "local.properties").write_text("private fixture\n")
        self.assertEqual(evidence.local_config(source, checkout, ["local.properties"]), ["local.properties"])
        self.assertTrue((checkout / "local.properties").is_symlink())
        self.assertEqual(git(checkout, "status", "--porcelain"), "")
        self.assertNotIn("private fixture", (evidence.output / "result.json").read_text())
        with self.assertRaises(VerificationError):
            evidence.local_config(source, checkout, ["source.txt"])
        (source / "unignored.properties").write_text("private fixture\n")
        with self.assertRaises(VerificationError):
            evidence.local_config(source, checkout, ["unignored.properties"])

    def test_output_cannot_reuse_stale_evidence(self):
        Evidence(self.root / "output", "candidate")
        with self.assertRaises(FileExistsError):
            Evidence(self.root / "output", "candidate")

    def test_failed_process_is_never_reported_as_passed(self):
        evidence = Evidence(self.root / "output", "candidate")
        with self.assertRaises(VerificationError):
            evidence.run("failure", [sys.executable, "-c", "raise SystemExit(7)"], self.root)
        self.assertEqual(evidence.data["stages"][-1]["exitCode"], 7)
        self.assertEqual(evidence.data["stages"][-1]["status"], "failed")
        evidence.finish("failed", "Test command failed")
        self.assertEqual(json.loads((evidence.output / "result.json").read_text())["status"], "failed")

    def test_missing_executable_records_failure(self):
        evidence = Evidence(self.root / "output", "candidate")
        with self.assertRaises(FileNotFoundError):
            evidence.run("missing", [str(self.root / "missing-executable")], self.root)
        self.assertEqual(evidence.data["stages"][-1]["status"], "failed")

    def test_timeout_stops_process_and_records_incomplete_stage(self):
        evidence = Evidence(self.root / "output", "candidate")
        with self.assertRaises(subprocess.TimeoutExpired):
            evidence.run("timeout", [sys.executable, "-c", "import time; time.sleep(30)"], self.root, timeout=0.1)
        self.assertEqual(evidence.data["stages"][-1]["status"], "interrupted")

    def test_missing_failing_and_all_skipped_junit_are_rejected(self):
        with self.assertRaises(VerificationError):
            junit_results(self.root)
        for counts in ('tests="0"', 'tests="2" failures="1"', 'tests="2" errors="1"',
                       'tests="2" skipped="2"'):
            with self.subTest(counts=counts):
                (self.root / "TEST-fixture.xml").write_text(f"<testsuite {counts}/>")
                with self.assertRaises(VerificationError):
                    junit_results(self.root)
        (self.root / "TEST-fixture.xml").write_text('<testsuite tests="3" skipped="1"/>')
        self.assertEqual(junit_results(self.root)["tests"], 3)

    def test_adb_success_exit_does_not_hide_instrumentation_failure(self):
        for output in ("", "OK (0 tests)", "FAILURES!!!\nOK (2 tests)",
                       "INSTRUMENTATION_FAILED: runner\nOK (2 tests)",
                       "INSTRUMENTATION_ABORTED: crash"):
            with self.subTest(output=output), self.assertRaises(VerificationError):
                instrumentation_count(output)
        self.assertEqual(instrumentation_count("Time: 1.2\nOK (3 tests)\n"), 3)

    def test_matrix_requires_immutable_pins_and_contained_config_paths(self):
        baseline = load_matrix(Path(__file__).with_name("matrix.json"))
        changes = [("commit", "main"), ("localConfig", ["../secret"]),
                   ("localConfig", ["/tmp/secret"]), ("localConfig", ["."]),
                   ("checks", ["shell command"]), ("deviceClasses", [])]
        for key, value in changes:
            with self.subTest(key=key, value=value):
                matrix = copy.deepcopy(baseline)
                matrix["consumers"]["snapmosaic"][key] = value
                path = self.root / "matrix.json"
                path.write_text(json.dumps(matrix))
                with self.assertRaises(VerificationError):
                    load_matrix(path)


if __name__ == "__main__":
    unittest.main()
