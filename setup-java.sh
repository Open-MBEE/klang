#!/bin/bash

# Shared Java setup script for K Language project
# Sources this file to set up JAVA_HOME and PATH
# Usage: source setup-java.sh
#        or: . setup-java.sh

# Only set up Java if JAVA_HOME is not already set or invalid
if [ -z "$JAVA_HOME" ] || [ ! -d "$JAVA_HOME" ]; then
    if [ -d "$HOME/.sdkman/candidates/java/current" ]; then
        # SDKMAN current - handle both macOS and Linux directory structures
        if [ -d "$HOME/.sdkman/candidates/java/current/Contents/Home" ]; then
            # macOS structure
            export JAVA_HOME="$HOME/.sdkman/candidates/java/current/Contents/Home"
        else
            # Linux structure
            export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
        fi
        export PATH="$JAVA_HOME/bin:$PATH"
    elif [ -d "$HOME/.sdkman/candidates/java/21.0.8-tem" ]; then
        if [ -d "$HOME/.sdkman/candidates/java/21.0.8-tem/Contents/Home" ]; then
            export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-tem/Contents/Home"
        else
            export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-tem"
        fi
        export PATH="$JAVA_HOME/bin:$PATH"
    elif [ -d "$HOME/.sdkman/candidates/java/21.0.2-open" ]; then
        if [ -d "$HOME/.sdkman/candidates/java/21.0.2-open/Contents/Home" ]; then
            export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.2-open/Contents/Home"
        else
            export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.2-open"
        fi
        export PATH="$JAVA_HOME/bin:$PATH"
    elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
        # macOS system Java - prefer Java 21, fallback to any Java
        export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home 2>/dev/null)
    fi
fi

