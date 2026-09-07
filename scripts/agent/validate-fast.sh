#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "[validate-fast] ERROR: not inside a Git worktree" >&2
  exit 2
}
cd "$repo_root"

echo "[validate-fast] V0: git diff --check"
git diff --check

echo "[validate-fast] V1: ./gradlew test"
./gradlew test

echo "[validate-fast] PASS"
