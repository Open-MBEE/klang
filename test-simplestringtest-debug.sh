#!/bin/bash

# Test RocketMachine with debug output to see generated SMT

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "Testing RocketMachine with Debug Output"
echo "========================================"
echo ""

# Ensure Java 21
if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk use java 21.0.3-tem 2>/dev/null || true
fi

echo "✅ Using Java:"
java -version 2>&1 | head -1
echo ""

# Select Z3 libraries
if [ -f "$PROJECT_ROOT/select-z3-architecture.sh" ]; then
    "$PROJECT_ROOT/select-z3-architecture.sh"
    echo ""
fi

# Set library path for Z3
case "$(uname -s)" in
    Darwin*)
        export DYLD_LIBRARY_PATH="$PROJECT_ROOT/lib:$DYLD_LIBRARY_PATH"
        ;;
    Linux*)
        export LD_LIBRARY_PATH="$PROJECT_ROOT/lib:$LD_LIBRARY_PATH"
        ;;
esac

# Create a temporary modified version of RocketMachine with debug enabled
# We'll do this by running a small Scala snippet that sets K2Z3.debug = true

cd "$PROJECT_ROOT"

# Build classpath
CP="target/classes"
CP="$CP:lib/com.microsoft.z3.jar"
for jar in export/lib/scalalib/*.jar; do
    CP="$CP:$jar"
done
for jar in export/lib/elasticsearch-1.5.0/*.jar; do
    [ -f "$jar" ] && CP="$CP:$jar" 2>/dev/null || true
done
CP="$CP:src/grammar/antlr-4.7-complete.jar"

echo "🚀 Running RocketMachine.k with debug enabled..."
echo ""
echo "This will generate:"
echo "  - /tmp/k_smt_model.log (generated SMT-LIB code)"
echo "  - Console output showing Z3 solving"
echo ""

# Run with debug by setting Java property
java -cp "$CP" \
    -Djava.library.path="$PROJECT_ROOT/lib" \
    -Dk2z3.debug=true \
    k.frontend.Main \
    -f src/test/RocketMachine.k 2>&1 | tee /tmp/rocketmachine-debug.log

echo ""
echo "========================================"
echo ""

# Check if SMT was generated
if [ -f /tmp/k_smt_model.log ]; then
    echo "✅ Generated SMT-LIB code:"
    echo ""
    head -100 /tmp/k_smt_model.log
    echo ""
    echo "[Full SMT output in /tmp/k_smt_model.log]"
else
    echo "⚠️  No SMT log file generated at /tmp/k_smt_model.log"
    echo ""
    echo "Checking if debug flag worked..."
    # Try to extract error details
    if grep -q "Exception" /tmp/simplestringtest-debug.log 2>/dev/null; then
        echo "Found exception in output:"
        grep -A 5 "Exception" /tmp/simplestringtest-debug.log
    fi
fi

echo ""
echo "Full output saved to: /tmp/simplestringtest-debug.log"


