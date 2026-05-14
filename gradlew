#!/bin/sh
##############################################################################
# Gradle start up script for UN*X
##############################################################################
APP_HOME="${0%/*}/.."
APP_HOME="$(cd "$APP_HOME" && pwd)"
APP_NAME="Gradle"
APP_BASE_NAME="${0##*/}"
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Find java
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
