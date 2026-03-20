#!/bin/bash
# Gradle Wrapper Setup Script
# Run this script to download the Gradle Wrapper JAR

GRADLE_WRAPPER_VERSION="8.13"
GRADLE_WRAPPER_URL="https://github.com/gradle/gradle/raw/v${GRADLE_WRAPPER_VERSION}/gradle/wrapper/gradle-wrapper.jar"

echo "Downloading Gradle Wrapper JAR..."
mkdir -p gradle/wrapper
curl -L -o gradle/wrapper/gradle-wrapper.jar "$GRADLE_WRAPPER_URL"
echo "Done!"
