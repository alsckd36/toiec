#!/usr/bin/env bash
set -euo pipefail
cd /workspace/toiec
source scripts/env.sh
# Published environments retain the verified JDK, CLI, SDK and Gradle caches.
# Refresh dependencies and proxy settings; do not regenerate source or wrapper files.
test -x "$JAVA_HOME/bin/javac"
test -x "$ANDROID_HOME/cmdline-tools/latest/bin/android"
python3 scripts/configure-gradle-proxy.py
android sdk install platforms/android-36 build-tools/36.0.0 platform-tools
cd android
./gradlew --no-daemon --max-workers=2 :app:assembleDebug
