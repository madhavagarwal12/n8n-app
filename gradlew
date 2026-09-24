#!/usr/bin/env bash

# Resolve APP_HOME
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Determine Java command
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if [ -f "$CLASSPATH" ]; then
    exec "$JAVACMD" "-Dorg.gradle.appname=gradlew" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
else
    # Fallback to system gradle if wrapper jar is not present
    if command -v gradle >/dev/null 2>&1; then
        exec gradle "$@"
    else
        echo "Error: Neither gradle-wrapper.jar nor system gradle found."
        exit 1
    fi
fi
