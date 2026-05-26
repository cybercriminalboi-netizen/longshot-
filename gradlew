#!/bin/sh

# Set native line endings for UNIX/Linux systems

# Save path to this script's directory
DIRNAME=`dirname "$0"`
APP_BASE_NAME=`basename "$0"`
APP_HOME=`cd "$DIRNAME" >/dev/null; pwd`

# Locate Java executable
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Run gradle wrapper
exec "$JAVACMD" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
