# Source this file in managed cloud tasks. Ordinary Android Studio users do not need it.
TICKET_TOOLCHAINS="${TICKET_TOOLCHAINS:-/workspace/toolchains}"
export JAVA_HOME="$TICKET_TOOLCHAINS/jdk-21.0.6+7"
export ANDROID_HOME="$TICKET_TOOLCHAINS/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_AVD_HOME="$TICKET_TOOLCHAINS/avd"
export GRADLE_USER_HOME="$TICKET_TOOLCHAINS/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
if [ -f /etc/ssl/certs/java/cacerts ]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
if [ -n "${HTTPS_PROXY:-}" ]; then
  TICKET_PROXY_ARGS="$(python3 - <<'PY'
import os
from urllib.parse import urlsplit
p=urlsplit(os.environ['HTTPS_PROXY'])
assert p.hostname and p.port
print(f'-Dhttps.proxyHost={p.hostname} -Dhttps.proxyPort={p.port} -Dhttp.proxyHost={p.hostname} -Dhttp.proxyPort={p.port} -Dhttp.nonProxyHosts=localhost|127.*')
PY
)"
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} $TICKET_PROXY_ARGS"
fi
