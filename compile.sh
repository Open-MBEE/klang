#!/bin/bash

# K Language - Maven Compile
# This script compiles the K language using Maven
# Requires Java 11+ (Java 21 recommended for Scala 2.13)

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

# Setup Java from SDKMAN or system
source "$PROJECT_ROOT/setup-java.sh"

echo "K Language Maven Compile"
echo "========================"
echo ""

# Verify Java is available
if ! command -v java &> /dev/null; then
    echo "❌ Java not found!"
    echo ""
    echo "This project requires Java 11+ (Java 21 recommended for Scala 2.13)."
    echo ""
    echo "To install Java using SDKMAN:"
    echo "  curl -s 'https://get.sdkman.io' | bash"
    echo "  source \"\$HOME/.sdkman/bin/sdkman-init.sh\""
    echo "  sdk install java"
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

