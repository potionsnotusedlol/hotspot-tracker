#!/usr/bin/env bash
# Makes real commits in a throwaway repository to check that the git hooks accept and reject what they should.
# Usage: test-git-hooks.sh [repository-root]
# Set SKIP_SECRET_CASES=1 to skip the cases that depend on the built-in secret patterns (e.g. when gitleaks is installed).
set -uo pipefail

src=$(cd "${1:-.}" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cd "$work" || exit 1

git init -q
git config core.autocrlf false
git config user.email test@example.com
git config user.name test
git config core.hooksPath .githooks
mkdir -p ops/ci .githooks
cp "$src/ops/ci/check-repo-rules.sh" ops/ci/
cp "$src/.githooks/pre-commit" "$src/.githooks/commit-msg" .githooks/
chmod +x .githooks/*

migrations=server/src/main/resources/db/migration
mkdir -p "$migrations"
echo 'create table a(id int);' > "$migrations/V202611011200__a.sql"
git add -A
if ! git commit -qm "chore: base"; then
  echo "FAIL: the base commit was rejected"
  exit 1
fi
base=$(git rev-parse HEAD)
failures=0

# Usage: expect ok|rejected "description" "commit message"
expect() {
  local output result
  if output=$(git commit -qm "$3" 2>&1); then result=ok; else result=rejected; fi
  if [ "$result" = "$1" ]; then
    echo "PASS  $2"
  else
    echo "FAIL  $2: expected $1, got $result"
    echo "$output" | sed 's/^/      /'
    failures=$((failures + 1))
  fi
  git reset -q --hard "$base"
  git clean -qfdx
}

stage() {
  mkdir -p "$(dirname "$1")"
  printf '%s\n' "$2" > "$1"
  git add -f "$1"
}

stage a.txt hello; expect ok "good message" "docs: add notes"
stage a.txt hello; expect ok "scope and breaking mark" "feat(server)!: change api"
stage a.txt hello; expect rejected "message without type" "added stuff"
stage a.txt hello; expect rejected "unknown type" "update: something"
stage a.txt hello; expect rejected "non-English message" "$(printf 'docs: \320\277\321\200\320\270\320\262\320\265\321\202')"
stage a.txt hello; expect ok "merge message" "Merge branch 'x' into y"
stage server/.gradle/cache bin; expect rejected "tracked .gradle" "chore: add files"
stage tgbot/target/app.jar bin; expect rejected "tracked target" "chore: add files"
stage ops/.env 'A=1'; expect rejected "tracked .env" "chore: add files"
stage ops/compose/.env.example 'A=1'; expect ok ".env.example" "chore: add files"
stage "$migrations/V1__auth.sql" 'select 1;'; expect rejected "migration without timestamp" "feat: add migration"
stage "$migrations/V202611021200__b.sql" 'select 1;'; expect ok "timestamped migration" "feat: add migration"
printf -- '-- changed\n' >> "$migrations/V202611011200__a.sql"; git add -A
expect rejected "changed migration" "fix: change migration"
stage ops/compose.yml 'image: postgres:latest'; expect rejected "latest image tag" "chore: add image"
stage ops/compose.yml 'image: postgres:18.1'; expect ok "pinned image tag" "chore: add image"
stage Config.java 'String password = "correct-horse-battery";'; expect ok "ordinary test password" "test: add config"

if [ "${SKIP_SECRET_CASES:-0}" != "1" ]; then
  # Built at run time, so the repository itself contains no token-like string
  stage tgbot/token.txt "1234567890:$(printf 'A%.0s' {1..35})"; expect rejected "Telegram bot token" "chore: add token"
  stage key.pem '-----BEGIN RSA PRIVATE KEY-----'; expect rejected "private key" "chore: add key"
fi

echo "bash $BASH_VERSION, $(git --version)"
if [ "$failures" -ne 0 ]; then
  echo "$failures case(s) failed"
  exit 1
fi
echo "All cases passed"
