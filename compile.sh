#!/bin/bash

# K Language - Maven Compile with Java 21
# This script ensures Java 21 is used for Scala 2.13 compatibility

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

# Function to check if Java 21 is available
check_java21() {
    # Check SDKMAN installation first
    if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
        source "$HOME/.sdkman/bin/sdkman-init.sh"

        # Try to find any Java 21 version
        if sdk list java 2>/dev/null | grep -q "21\."; then
            # Use sdk to switch to Java 21 (finds first available)
            sdk use java 21.0.3-tem 2>/dev/null || \
            sdk use java 21.0.9-amzn 2>/dev/null || \
            sdk use java 21.0.2-open 2>/dev/null || \
            return 1
            return 0
        fi
    fi

    # Fallback: check known paths
    if [ -d "$HOME/.sdkman/candidates/java/21.0.3-tem" ]; then
        export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.3-tem"
        export PATH="$JAVA_HOME/bin:$PATH"
        return 0
    fi

    return 1
}

echo "K Language Maven Compile"
echo "========================"
echo ""

# Check for Java 21
if ! check_java21; then
    echo "❌ Java 21 not found!"
    echo ""
    echo "This project requires Java 21 for Scala 2.13 compatibility."
    echo ""
    echo "To install Java 21 using SDKMAN:"
    echo "  curl -s 'https://get.sdkman.io' | bash"
    echo "  source \"\$HOME/.sdkman/bin/sdkman-init.sh\""
    echo "  sdk install java 21.0.3-tem"
    echo ""
    echo "Then run this script again."
    exit 1
fi

echo "✅ Using Java 21:"
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

