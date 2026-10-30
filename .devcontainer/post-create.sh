#!/usr/bin/env bash
# Installs the Android SDK into the codespace and writes a local.properties for it.
set -euo pipefail

SDK="${ANDROID_HOME:-/home/vscode/android-sdk}"
# Pinned to 21.0, the last release with the classic sdkmanager. From 22.0 sdkmanager hands off to the
# new `android` CLI, and in 23.0 `--licenses` is a no-op ("no longer needed") that writes no licence
# files. AGP still reads those files, so every SDK download on the first build failed.
TOOLS_REVISION=21.0
TOOLS_ZIP=commandlinetools-linux-15641748_latest.zip
TOOLS_SHA256=a66d5ef0238fc0162e9c1446602ce0dd41702d4dd7a94d2ce42d12b7f80baf7e

if ! grep -qx "Pkg.Revision=$TOOLS_REVISION" "$SDK/cmdline-tools/latest/source.properties" 2>/dev/null; then
  rm -rf "$SDK/cmdline-tools/latest"
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

# The template's sdk.dir is a macOS path. Take its Android Sentry DSN plus signing placeholders;
# Gradle reads public Appwrite defaults from shared/backend.json.
if [ ! -f android/local.properties ]; then
  {
    echo "sdk.dir=$SDK"
    grep -E '^SENTRY_DSN=' android/local.properties.template
    printf 'KEYSTORE_PASSWORD=placeholder\nKEY_ALIAS=placeholder\nKEY_PASSWORD=placeholder\n'
  } > android/local.properties
fi
