#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "[validate-build] ERROR: not inside a Git worktree" >&2
  exit 2
}
cd "$repo_root"

echo "[validate-build] V0: git diff --check"
git diff --check

echo "[validate-build] V1: ./gradlew test"
./gradlew test

echo "[validate-build] V2: ./gradlew build"
./gradlew build

echo "[validate-build] PASS"
