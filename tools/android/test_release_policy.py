"""Release provenance and workflow separation regressions (no network/secrets)."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("release_source", Path(__file__).with_name("verify-release-source.py"))
source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(source)
SHA = "a" * 40
REPO = "monkeCode/bookorbit-android"


def pr(**changes):
    result = {"merged_at": "2026-11-26T00:00:00Z", "merge_commit_sha": SHA,
              "base": {"ref": "main", "repo": {"full_name": REPO}}}
    result.update(changes)
    return result


class ReleasePolicyTest(unittest.TestCase):
    def check(self, status, prs):
        with patch.object(source, "api", side_effect=[{"status": status}, prs]):
            source.verify(REPO, SHA)

    def test_accepts_only_exact_merged_result_on_main(self):
        for status in ("ahead", "identical"):
            self.check(status, [pr()])

    def test_rejects_unmerged_wrong_base_and_non_merge_commits(self):
        for prs in ([], [pr(merged_at=None)], [pr(merge_commit_sha="b" * 40)],
                    [pr(base={"ref": "other", "repo": {"full_name": REPO}})],
                    [pr(base={"ref": "main", "repo": {"full_name": "other/repo"}})]):
            with self.assertRaises(ValueError):
                self.check("identical", prs)

    def test_rejects_non_main_history_and_non_sha_input(self):
        for status in ("behind", "diverged"):
            with self.assertRaises(ValueError):
                self.check(status, [pr()])
        with patch.object(source, "api") as api:
            for sha in ("main", "v1.0", "$(command)", "a" * 39):
                with self.assertRaises(ValueError):
                    source.verify(REPO, sha)
            api.assert_not_called()

    def test_normal_build_has_no_signing_secret_or_environment(self):
        text = (Path(__file__).parents[2] / ".github/workflows/android-build.yml").read_text()
        self.assertNotIn("secrets.", text)
        self.assertNotIn("environment:", text)
        self.assertNotIn("Release --", text)

    def test_only_release_publish_job_uses_debug_key(self):
        text = (Path(__file__).parents[2] / ".github/workflows/debug-release.yml").read_text()
        before, publish = text.split("  publish:", 1)
        self.assertNotIn("secrets.", before)
        self.assertIn("environment: debug-release", publish)
        self.assertIn("needs: [provenance, build]", publish)
        self.assertIn("secrets.DEBUG_KEYSTORE_BASE64", publish)
        self.assertNotIn("uses: actions/checkout", publish)
        self.assertNotIn("./gradlew", publish)


if __name__ == "__main__":
    unittest.main()
