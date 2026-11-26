#!/usr/bin/env bash
set -euo pipefail
mkdir -p "$ANDROID_AVD_HOME"
if [[ ! -f "$ANDROID_AVD_HOME/bookorbit-test.ini" ]]; then
  printf 'no\n' | avdmanager create avd --force --name bookorbit-test \
    --package 'system-images;android-36;google_apis;x86_64' --device pixel_6
fi
exec /opt/android/emulator/emulator -avd bookorbit-test \
  -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect -cores 2 -memory 2048 \
  -camera-back none -camera-front none
