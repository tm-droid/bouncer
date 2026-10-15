#!/usr/bin/env bash
# Installs the Android SDK into the codespace and writes a local.properties for it.
set -euo pipefail

SDK="${ANDROID_HOME:-/home/vscode/android-sdk}"
TOOLS_ZIP=commandlinetools-linux-16111833_latest.zip
TOOLS_SHA256=0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$TOOLS_ZIP"
  echo "$TOOLS_SHA256  $tmp/tools.zip" | sha256sum --check
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

# `yes` dies of SIGPIPE when sdkmanager exits, and under pipefail that 141 would end the script
# whether or not the licences were written, so check for the licence file instead.
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
if [ ! -f "$SDK/licenses/android-sdk-license" ]; then
  echo "post-create: Android SDK licences were not accepted" >&2
  exit 1
fi
# The ids Gradle asks for by name: compileSdk 37 is `android-37.0`, and AGP 9.3's default build-tools.
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"

# The template's sdk.dir is a macOS path. Take only its public Appwrite ids and Sentry DSN, as the CI
# release job does, plus signing placeholders so the release variant configures.
if [ ! -f local.properties ]; then
  {
    echo "sdk.dir=$SDK"
    grep -E '^(APPWRITE_[A-Z_]+|SENTRY_DSN)=' local.properties.template
    printf 'KEYSTORE_PASSWORD=placeholder\nKEY_ALIAS=placeholder\nKEY_PASSWORD=placeholder\n'
  } > local.properties
fi
