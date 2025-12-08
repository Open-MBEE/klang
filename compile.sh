#!/bin/bash

# K Language - Maven Compile with Java 21
# This script ensures Java 21 is used for Scala 2.13 compatibility

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

# Setup Java 21
if [ -d "$HOME/.sdkman/candidates/java/current" ]; then
    export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -d "$HOME/.sdkman/candidates/java/21.0.8-tem" ]; then
    export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-tem"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -d "$HOME/.sdkman/candidates/java/21.0.2-open" ]; then
    export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.2-open"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "K Language Maven Compile"
echo "========================"
echo ""

# Verify Java is available
if ! command -v java &> /dev/null; then
    echo "❌ Java not found!"
    echo ""
    echo "This project requires Java 21 for Scala 2.13 compatibility."
    echo ""
    echo "To install Java 21 using SDKMAN:"
    echo "  curl -s 'https://get.sdkman.io' | bash"
    echo "  source \"\$HOME/.sdkman/bin/sdkman-init.sh\""
    echo "  sdk install java 21.0.8-tem"
    echo ""
    echo "Then run this script again."
    exit 1
fi

echo "✅ Using Java:"
java -version 2>&1 | head -1
echo ""

# Select appropriate Z3 libraries
if [ -f "$PROJECT_ROOT/select-z3-architecture.sh" ]; then
    "$PROJECT_ROOT/select-z3-architecture.sh"
    echo ""
fi

# Run Maven compile
echo "🔨 Running Maven compile..."
echo ""

cd "$PROJECT_ROOT"
mvn compile "$@"

echo ""
echo "✅ Build complete!"

