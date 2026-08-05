#!/usr/bin/env bash
# Ensure the Pixel_9 AVD exists, creating it (and installing the matching
# system image) if necessary. Works on macOS (arm64) and Linux (x86_64).
#
# The AVD lives in ~/.android/avd and is not committed, so a fresh clone has
# no AVD. `devbox run emulator` calls this first to self-heal that.
set -euo pipefail

AVD_NAME="${1:-Pixel_9}"
API="android-36"
TAG="google_apis_playstore"

: "${ANDROID_HOME:?ANDROID_HOME must be set (devbox init_hook sets it)}"

# Pick the ABI that matches the host so hardware acceleration works.
case "$(uname -m)" in
  arm64 | aarch64) ABI="arm64-v8a" ;;
  x86_64 | amd64)  ABI="x86_64" ;;
  *) echo "Unsupported host arch: $(uname -m)" >&2; exit 1 ;;
esac

PKG="system-images;${API};${TAG};${ABI}"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

if "$ANDROID_HOME/emulator/emulator" -list-avds 2>/dev/null | grep -qx "$AVD_NAME"; then
  echo "AVD '$AVD_NAME' already exists."
  exit 0
fi

# Install the system image if it isn't present yet.
if [ ! -d "$ANDROID_HOME/system-images/${API}/${TAG}/${ABI}" ]; then
  echo "Installing system image: $PKG"
  yes | "$SDKMANAGER" "$PKG"
fi

echo "Creating AVD '$AVD_NAME' ($PKG)..."
echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$PKG" -d pixel_7
echo "AVD '$AVD_NAME' created."
