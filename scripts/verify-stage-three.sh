#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/Users/chunlin/.sdkman/candidates/java/current}"
MAVEN_HOME="${MAVEN_HOME:-/Users/chunlin/Documents/maven-3.9.16}"
MAVEN="$MAVEN_HOME/bin/mvn"

test -x "$JAVA_HOME/bin/java"
test -x "$MAVEN"
command -v node >/dev/null

cd "$ROOT"
JAVA_HOME="$JAVA_HOME" "$MAVEN" -Dmaven.repo.local=.m2 clean test package
node scripts/verify-js-contract.mjs
JAVA_HOME="$JAVA_HOME" "$MAVEN" -Dmaven.repo.local=.m2 -Pwindows-x64 clean package -DskipTests
"$JAVA_HOME/bin/jar" tf target/offline-asr-0.1.0-SNAPSHOT.jar | grep -q 'BOOT-INF/lib/sherpa-onnx-native-lib-win-x64-2.0.3.jar'
if "$JAVA_HOME/bin/jar" tf target/offline-asr-0.1.0-SNAPSHOT.jar | grep -q 'osx-aarch64'; then
  echo 'Windows profile incorrectly contains macOS native library' >&2
  exit 1
fi
echo 'Stage three automated verification: PASS'
