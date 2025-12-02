#!/bin/bash

# Test the RocketMachine string example

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "Testing RocketMachine String Example"
echo "====================================="
echo ""

# Ensure Java 8 is being used
if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk use java 8.0.422-tem 2>/dev/null || true
fi

echo "✅ Using Java:"
java -version 2>&1 | head -1
echo ""

# Select appropriate Z3 libraries
if [ -f "$PROJECT_ROOT/select-z3-architecture.sh" ]; then
    "$PROJECT_ROOT/select-z3-architecture.sh"
    echo ""
fi

# Compile if needed
if [ ! -d "$PROJECT_ROOT/target/classes" ]; then
    echo "🔨 Building project first..."
    "$PROJECT_ROOT/compile.sh"
    echo ""
fi

# Set up library path for Z3
case "$(uname -s)" in
    Darwin*)
        export DYLD_LIBRARY_PATH="$PROJECT_ROOT/lib:$DYLD_LIBRARY_PATH"
        ;;
    Linux*)
        export LD_LIBRARY_PATH="$PROJECT_ROOT/lib:$LD_LIBRARY_PATH"
        ;;
esac

# Run the K frontend on RocketMachine.k
echo "🚀 Processing RocketMachine.k..."
echo ""

cd "$PROJECT_ROOT"

# Build classpath
CP="target/classes"
CP="$CP:lib/com.microsoft.z3.jar"
for jar in export/lib/scalalib/*.jar; do
    CP="$CP:$jar"
done
for jar in export/lib/elasticsearch-1.5.0/*.jar; do
    CP="$CP:$jar"
done
CP="$CP:src/grammar/antlr-4.7-complete.jar"

# Run K frontend with RocketMachine example
# Using -typecheck and -smt flags to verify String support
java -cp "$CP" \
    -Djava.library.path="$PROJECT_ROOT/lib" \
    k.frontend.Main \
    -f src/test/RocketMachine.k \
    -typecheck \
    -smt 2>&1 | tee /tmp/rocketmachine-test.log

echo ""
echo "✅ Test complete! Check output above."
echo ""
echo "Expected results:"
echo "  - Type checking should succeed"
echo "  - SMT generation should include String sort and string literals"
echo "  - Z3 should report SAT (satisfiable)"
echo ""
echo "Full log saved to: /tmp/simplestringtest-test.log"


