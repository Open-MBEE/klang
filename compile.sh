#!/bin/bash

# K Language - Maven Compile with Java 8
# This script ensures Java 8 is used for Scala 2.11 compatibility

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

# Function to check if Java 8 is available
check_java8() {
    # Check SDKMAN installation first
    if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
        source "$HOME/.sdkman/bin/sdkman-init.sh"

        # Try to find any Java 8 version
        if sdk list java 2>/dev/null | grep -q "8\.0"; then
            # Use sdk to switch to Java 8 (finds first available)
            sdk use java 8.0.422-tem 2>/dev/null || \
            sdk use java 8.0.472-tem 2>/dev/null || \
            sdk use java 8.0.472-zulu 2>/dev/null || \
            sdk use java 8.0.462-zulu 2>/dev/null || \
            return 1
            return 0
        fi
    fi

    # Fallback: check known paths
    if [ -d "$HOME/.sdkman/candidates/java/8.0.462-zulu" ]; then
        export JAVA_HOME="$HOME/.sdkman/candidates/java/8.0.462-zulu"
        export PATH="$JAVA_HOME/bin:$PATH"
        return 0
    elif [ -d "/Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home" ]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home"
        export PATH="$JAVA_HOME/bin:$PATH"
        return 0
    fi

    return 1
}

echo "K Language Maven Compile"
echo "========================"
echo ""

# Check for Java 8
if ! check_java8; then
    echo "❌ Java 8 not found!"
    echo ""
    echo "This project requires Java 8 due to Scala 2.11.8 compatibility."
    echo ""
    echo "To install Java 8 using SDKMAN:"
    echo "  curl -s 'https://get.sdkman.io' | bash"
    echo "  source \"\$HOME/.sdkman/bin/sdkman-init.sh\""
    echo "  sdk install java 8.0.472-tem"
    echo ""
    echo "Then run this script again."
    exit 1
fi

echo "✅ Using Java 8:"
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

