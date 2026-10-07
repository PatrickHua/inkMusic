#!/usr/bin/env bash
# Build a release APK signed with your key and install it on the connected phone.
# The keystore password is read from the macOS Keychain (service "inkmusic-keystore").
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export INKMUSIC_KEYSTORE="${INKMUSIC_KEYSTORE:-$HOME/.android-keys/inkmusic-release.jks}"
INKMUSIC_KEYSTORE_PASSWORD="$(security find-generic-password -s inkmusic-keystore -w)"
export INKMUSIC_KEYSTORE_PASSWORD

./gradlew --no-daemon -q assembleRelease
APK=app/build/outputs/apk/release/app-release.apk

if [[ "${1:-}" != "--no-install" ]]; then
  adb install -r "$APK"
fi
echo "$APK"
