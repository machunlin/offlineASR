#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)
JAVA_HOME="/Users/chunlin/.sdkman/candidates/java/current"
MAVEN_HOME="/Users/chunlin/Documents/maven-3.9.16"
test -x "$JAVA_HOME/bin/java"
test -x "$MAVEN_HOME/bin/mvn"
test -f "$ROOT/models/SenseVoiceSmall/model.int8.onnx"
test -f "$ROOT/models/SenseVoiceSmall/tokens.txt"
command -v ffmpeg >/dev/null
cd "$ROOT"
JAVA_HOME="$JAVA_HOME" MAVEN_HOME="$MAVEN_HOME" "$MAVEN_HOME/bin/mvn" -Dmaven.repo.local=.m2 spring-boot:run
