#!/usr/bin/env bash
set -euo pipefail
TICKET_REPO="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TICKET_TOOLCHAINS="${TICKET_TOOLCHAINS:-/workspace/toolchains}"
mkdir -p "$TICKET_TOOLCHAINS"
TICKET_DOWNLOADS="$(mktemp -d)"
trap 'rm -rf "$TICKET_DOWNLOADS"' EXIT
if [ ! -x "$TICKET_TOOLCHAINS/jdk-21.0.6+7/bin/javac" ]; then
  curl --fail --location --retry 2 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.6%2B7/OpenJDK21U-jdk_x64_linux_hotspot_21.0.6_7.tar.gz' -o "$TICKET_DOWNLOADS/jdk.tar.gz"
  (cd "$TICKET_DOWNLOADS"; echo 'a2650fba422283fbed20d936ce5d2a52906a5414ec17b2f7676dddb87201dbae  jdk.tar.gz' | sha256sum --check)
  tar -xzf "$TICKET_DOWNLOADS/jdk.tar.gz" -C "$TICKET_TOOLCHAINS"
fi
if [ ! -x "$TICKET_TOOLCHAINS/android-sdk/cmdline-tools/19.0/bin/sdkmanager" ]; then
  curl --fail --location --retry 2 https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$TICKET_DOWNLOADS/sdk.zip"
  (cd "$TICKET_DOWNLOADS"; echo '5fdcc763663eefb86a5b8879697aa6088b041e70  sdk.zip' | sha1sum --check)
  unzip -q "$TICKET_DOWNLOADS/sdk.zip" -d "$TICKET_DOWNLOADS/sdk"
  mkdir -p "$TICKET_TOOLCHAINS/android-sdk/cmdline-tools"
  mv "$TICKET_DOWNLOADS/sdk/cmdline-tools" "$TICKET_TOOLCHAINS/android-sdk/cmdline-tools/19.0"
fi
source "$TICKET_REPO/scripts/cloud-env.sh"
TICKET_PROXY_FLAGS=()
if [ -n "${HTTPS_PROXY:-}" ]; then
  read -r TICKET_PROXY_HOST TICKET_PROXY_PORT < <(python3 - <<'PY'
import os
from urllib.parse import urlsplit
p=urlsplit(os.environ['HTTPS_PROXY']);print(p.hostname,p.port)
PY
)
  TICKET_PROXY_FLAGS=(--proxy=http "--proxy_host=$TICKET_PROXY_HOST" "--proxy_port=$TICKET_PROXY_PORT")
fi
# sdkmanager consumes enough 'y' answers; ignore only yes's expected broken pipe.
set +o pipefail
yes | "$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" --sdk_root="$ANDROID_HOME" "${TICKET_PROXY_FLAGS[@]}" --licenses > "$TICKET_DOWNLOADS/licenses.log"
TICKET_LICENSE_RESULT=${PIPESTATUS[1]}
set -o pipefail
if [ "$TICKET_LICENSE_RESULT" -ne 0 ]; then cat "$TICKET_DOWNLOADS/licenses.log"; exit "$TICKET_LICENSE_RESULT"; fi
"$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" --sdk_root="$ANDROID_HOME" "${TICKET_PROXY_FLAGS[@]}" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
# Use ANDROID_HOME instead of overwriting a developer's local.properties.
cd "$TICKET_REPO"
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
