#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/bookorbit-client"
mkdir -p "$cache/gradle" "$cache/home"
docker build -t bookorbit-client-android:local "$root/tools/android"
exec docker run --rm --init \
  --user "$(id -u):$(id -g)" \
  --env HOME=/build-home \
  --env GRADLE_USER_HOME=/gradle-cache \
  --env GRADLE_OPTS="-Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false" \
  --mount "type=bind,source=$root,target=/workspace" \
  --mount "type=bind,source=$cache/gradle,target=/gradle-cache" \
  --mount "type=bind,source=$cache/home,target=/build-home" \
  bookorbit-client-android:local \
  ./gradlew --no-daemon --max-workers=2 "$@"
