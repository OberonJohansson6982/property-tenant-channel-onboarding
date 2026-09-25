#!/bin/sh
set -eu
BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/property-onboarding.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/test-classes"
javac -d "$BUILD_DIR/classes" $(find src/main/java -name '*.java' | sort)
if [ "${1:-}" = "test" ]; then
  javac -cp "$BUILD_DIR/classes" -d "$BUILD_DIR/test-classes" $(find src/test/java -name '*.java' | sort)
  java -cp "$BUILD_DIR/classes:$BUILD_DIR/test-classes" example.property.PropertyOnboardingTest
else
  java -cp "$BUILD_DIR/classes" example.property.PropertyOnboardingApplication
fi
