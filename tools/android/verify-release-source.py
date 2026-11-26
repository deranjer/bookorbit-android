#!/usr/bin/env python3
"""Fail closed unless the exact commit is on main and is a merged PR result."""
import argparse
import json
import re
import subprocess


def api(repo, endpoint):
    return json.loads(subprocess.check_output(["gh", "api", f"repos/{repo}/{endpoint}"], text=True))


def verify(repo, sha):
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Expected an exact commit SHA")
    comparison = api(repo, f"compare/{sha}...main")
    if comparison["status"] not in ("ahead", "identical"):
        raise ValueError("Commit is not in main history")
    prs = api(repo, f"commits/{sha}/pulls?per_page=100")
    if not any(pr.get("merged_at") and pr["base"]["ref"] == "main"
               and pr["base"]["repo"]["full_name"].lower() == repo.lower()
               and pr.get("merge_commit_sha") == sha for pr in prs):
        raise ValueError("Commit must be the exact result of a merged PR into main")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo")
    parser.add_argument("sha")
    args = parser.parse_args()
    verify(args.repo, args.sha)
    print(f"Verified merged-PR source: {args.sha}")
