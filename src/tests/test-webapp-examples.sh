#!/bin/bash
# Test script for web application example files
# These are the examples shown in the K web application (k.html)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../.." || exit 1

# Setup Java
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk use java 21.0.3-tem > /dev/null 2>&1
fi

echo "======================================"
echo "K Web Application Examples Test Suite"
echo "======================================"
echo ""

# Function to get expected outcome for a file
get_expected() {
    case "$1" in
        "Shapes.k") echo "pass" ;;
        "sm.k") echo "pass" ;;
        "borges.k") echo "pass" ;;
        "prepost.k") echo "pass" ;;
        "Fruits.k") echo "pass" ;;
        "lightswitch.k") echo "pass" ;;
        "scheduling.k") echo "pass" ;;
        "planning-simple.k") echo "pass" ;;
        "StringDemo.k") echo "pass" ;;  # String operations demo
        "GravityScience.k") echo "exception" ;;  # Known issue with 'assoc' type
        "DSN_Pass.k") echo "exception" ;;  # Expected to throw exception
        *) echo "unknown" ;;
    esac
}

# Web app examples from k.html lines 35-44
WEBAPP_EXAMPLES="Shapes.k sm.k borges.k prepost.k Fruits.k lightswitch.k scheduling.k planning-simple.k StringDemo.k GravityScience.k DSN_Pass.k"

PASSED=0
FAILED=0
TOTAL=0

for EXAMPLE in $WEBAPP_EXAMPLES; do
    TOTAL=$((TOTAL + 1))
    printf "%-30s ... " "$EXAMPLE"

    OUTPUT=$(./export/k "src/examples/$EXAMPLE" 2>&1)

    EXPECTED=$(get_expected "$EXAMPLE")

    # Check for success indicators
    HAS_TYPE_CHECK=$(echo "$OUTPUT" | grep -c "Type checking completed" || true)
    HAS_STATISTICS=$(echo "$OUTPUT" | grep -c "STATISTICS" || true)
    HAS_EXCEPTION=$(echo "$OUTPUT" | grep -c "Exception\|Error" || true)
    HAS_OBJECTS=$(echo "$OUTPUT" | grep -c "objects created\|Top level" || true)

    if [ "$EXPECTED" = "exception" ]; then
        if [ "$HAS_EXCEPTION" -gt 0 ]; then
            echo "✅ PASSED (expected exception)"
            PASSED=$((PASSED + 1))
        else
            echo "❌ FAILED (expected exception, got success)"
            FAILED=$((FAILED + 1))
        fi
    elif [ "$EXPECTED" = "pass" ]; then
        if [ "$HAS_TYPE_CHECK" -gt 0 ] || [ "$HAS_STATISTICS" -gt 0 ] || [ "$HAS_OBJECTS" -gt 0 ]; then
            echo "✅ PASSED"
            PASSED=$((PASSED + 1))
        elif [ "$HAS_EXCEPTION" -gt 0 ]; then
            echo "❌ FAILED (unexpected exception)"
            FAILED=$((FAILED + 1))
            echo "    Error: $(echo "$OUTPUT" | grep -m1 'Exception\|Error')"
        else
            echo "❓ UNKNOWN"
            FAILED=$((FAILED + 1))
        fi
    else
        echo "❓ UNKNOWN (no expected outcome defined)"
        FAILED=$((FAILED + 1))
    fi
done

echo ""
echo "======================================"
echo "Summary:"
echo "  Total:  $TOTAL"
echo "  Passed: $PASSED"
echo "  Failed: $FAILED"
echo "======================================"

if [ "$FAILED" -gt 0 ]; then
    exit 1
else
    exit 0
fi

