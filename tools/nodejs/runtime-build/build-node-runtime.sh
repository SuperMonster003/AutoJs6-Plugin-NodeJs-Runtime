#!/usr/bin/env sh
set -eu

LOCK_FILE="${1:-tools/nodejs/runtime-build/runtime-build.lock.json}"
MODE="${2:-plan}"
REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"

if [ ! -f "$REPO_ROOT/$LOCK_FILE" ]; then
  echo "Missing runtime build lock file: $LOCK_FILE" >&2
  exit 1
fi

node "$REPO_ROOT/tools/nodejs/runtime-build/verify-runtime-build-plan.js" \
  --repo-root "$REPO_ROOT" \
  --lock-file "$REPO_ROOT/$LOCK_FILE"

if [ "$MODE" != "--execute" ]; then
  echo "Plan-only mode. Pass --execute to materialize the pinned Node 24.5 upstream binary archive."
  exit 0
fi

ARCHIVE_URL="$(node -p "require(process.argv[1]).androidFork.androidArtifact.url" "$REPO_ROOT/$LOCK_FILE")"
ARCHIVE_SHA="$(node -p "require(process.argv[1]).androidFork.androidArtifact.sha256" "$REPO_ROOT/$LOCK_FILE")"
ARCHIVE_SIZE="$(node -p "require(process.argv[1]).androidFork.androidArtifact.size" "$REPO_ROOT/$LOCK_FILE")"
ARCHIVE_FILE="${AUTOJS6_NODE_ARCHIVE:-$REPO_ROOT/build/runtime-build-cache/nodejs-mobile-24.05-android.zip}"
OUTPUT_ROOT="${AUTOJS6_NODE_OUTPUT:-$REPO_ROOT/build/node-runtime}"

mkdir -p "$(dirname "$ARCHIVE_FILE")" "$OUTPUT_ROOT"
if [ ! -f "$ARCHIVE_FILE" ]; then
  curl --fail --location --retry 2 --output "$ARCHIVE_FILE" "$ARCHIVE_URL"
fi
ACTUAL_SIZE="$(wc -c < "$ARCHIVE_FILE" | tr -d ' ')"
[ "$ACTUAL_SIZE" = "$ARCHIVE_SIZE" ] || { echo "Runtime archive size mismatch: expected $ARCHIVE_SIZE, actual $ACTUAL_SIZE" >&2; exit 1; }
printf '%s  %s\n' "$ARCHIVE_SHA" "$ARCHIVE_FILE" | sha256sum --check --status

for ABI in arm64-v8a armeabi-v7a x86_64; do
  ENTRY="$(node -p "require(process.argv[1]).androidFork.androidArtifact.entries[process.argv[2]].path" "$REPO_ROOT/$LOCK_FILE" "$ABI")"
  mkdir -p "$OUTPUT_ROOT/$ABI"
  unzip -p "$ARCHIVE_FILE" "$ENTRY" > "$OUTPUT_ROOT/$ABI/libnode.so"
done

node "$REPO_ROOT/tools/nodejs/runtime-build/verify-runtime-build-plan.js" \
  --repo-root "$REPO_ROOT" \
  --lock-file "$REPO_ROOT/$LOCK_FILE" \
  --materialized-root "$OUTPUT_ROOT"
echo "Pinned Node 24.5 runtime materialized at $OUTPUT_ROOT"
