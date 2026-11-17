#!/usr/bin/env bash
# Checks repository rules that general-purpose analyzers don't know about.
# Usage: check-repo-rules.sh [base-commit]
# Rules that compare changes are skipped when the base commit is missing or unknown.
set -euo pipefail

base="${1:-}"
migrations="server/src/main/resources/db/migration"
failed=0

fail() {
  echo "::error::$1"
  failed=1
}

# Build output, local caches and signing keys must not be tracked
while IFS= read -r file; do
  fail "Must not be committed: $file"
done < <(git ls-files | grep -E '(^|/)(\.gradle|build|target)/|\.(jks|keystore|p12)$' || true)

# A repository cloned inside the repository is committed as a gitlink
while IFS= read -r file; do
  fail "Nested repository committed: $file"
done < <(git ls-files -s | awk '$1 == "160000" { print $4 }')

# Timestamped names keep migrations from different branches from colliding
while IFS= read -r file; do
  name="${file##*/}"
  if ! grep -qE '^V[0-9]{12}__[a-z0-9_]+\.sql$' <<< "$name"; then
    fail "Migration name must look like V202610061530__create_table.sql: $file"
  fi
done < <(git ls-files "$migrations/*")

if [ -n "$base" ] && git cat-file -e "$base^{commit}" 2> /dev/null; then
  # Applied migrations are immutable: a change must be a new migration
  while IFS=$'\t' read -r status file; do
    fail "Existing migrations must not be changed ($status): $file"
  done < <(git diff --name-status --no-renames --diff-filter=MD "$base" HEAD -- "$migrations")

  # Images are pinned to an exact version
  while IFS= read -r line; do
    fail "Pin an exact image version instead of latest: ${line#+}"
  done < <(git diff -U0 "$base" HEAD -- '*.yml' '*.yaml' '*.java' '*.kts' '*Dockerfile*' \
    | grep -E '^\+[^+].*[a-z0-9._/-]+:latest\b' || true)
fi

exit "$failed"
