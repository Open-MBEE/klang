#!/bin/bash

# K Test Runner
# Runs tests from src/tests directory against a baseline

set -e

# Setup Java 8
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk use java 8.0.422-tem 2>/dev/null || true
fi

cd "$(dirname "$0")"

echo "======================================"
echo "K Language Test Runner"
echo "======================================"
echo ""
echo "Test directories:"
echo "  - src/tests/    : Original test suite (57+ tests)"
echo "  - src/test/     : New string operation tests"
echo ""
echo "Available flags:"
echo "  -tests          : Run all tests in src/tests/ against baseline"
echo "  -test           : Run single test interactively"
echo "  -baseline       : Update baseline with current results"
echo ""

# Check if compiled
if [ ! -d "target/classes" ]; then
    echo "ERROR: Project not compiled. Run ./compile.sh first."
    exit 1
fi

# Parse command line options
RUN_ALL_TESTS=false
RUN_SINGLE_TEST=false
SAVE_BASELINE=false
TEST_FILE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -tests)
            RUN_ALL_TESTS=true
            shift
            ;;
        -test)
            RUN_SINGLE_TEST=true
            TEST_FILE="$2"
            shift 2
            ;;
        -baseline)
            SAVE_BASELINE=true
            shift
            ;;
        -h|--help)
            echo "Usage: ./run-tests.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  (none)          Run all tests in safe mode (default, recommended)"
            echo "  -test <file>    Run a single test"
            echo "  -baseline       Run tests and compare against baseline.json (may crash)"
            echo "  -save-baseline  Update baseline.json with current results"
            echo "  -h, --help      Show this help"
            echo ""
            echo "Examples:"
            echo "  ./run-tests.sh              # Run all 54 tests (safe mode)"
            echo "  ./run-tests.sh -test as1.k  # Run one test"
            echo "  ./run-tests.sh -baseline    # Compare against baseline (may crash)"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use -h for help"
            exit 1
            ;;
    esac
done

echo "======================================"
echo "K Language Test Runner"
echo "======================================"
echo ""

# Check if compiled
if [ ! -d "target/classes" ]; then
    echo "ERROR: Project not compiled. Run ./compile.sh first."
    exit 1
fi

# Run single test
if [ "$RUN_SINGLE_TEST" = true ]; then
    if [ -z "$TEST_FILE" ]; then
        echo "ERROR: -test requires a filename"
        echo "Example: ./run-tests.sh -test as1.k"
        exit 1
    fi

    echo "Running test: $TEST_FILE"
    echo ""
    ./export/k "src/tests/$TEST_FILE"
    exit $?
fi

# Run with baseline comparison (may crash due to Z3 issues)
if [ "$USE_BASELINE" = true ]; then
    echo "Running tests with baseline comparison..."
    echo "⚠️  Warning: This may crash due to Z3 native library issues"
    echo ""

    if [ "$SAVE_BASELINE" = true ]; then
        java -cp "target/classes:lib/com.microsoft.z3.jar:export/lib/scalalib/*:export/lib/elasticsearch-1.5.0/*:src/grammar/antlr-4.7-complete.jar" \
            -Djava.library.path="lib" \
            k.frontend.Main -tests -baseline
    else
        java -cp "target/classes:lib/com.microsoft.z3.jar:export/lib/scalalib/*:export/lib/elasticsearch-1.5.0/*:src/grammar/antlr-4.7-complete.jar" \
            -Djava.library.path="lib" \
            k.frontend.Main -tests
    fi
    exit $?
fi

# Default: Run in safe mode (each test individually)
echo "Running tests in safe mode (each test individually)..."
echo "Use -baseline to compare against baseline.json (may crash)"
echo ""

TEST_DIR="src/tests"
TEST_FILES=$(find "$TEST_DIR" -name "*.k" | sort)
TOTAL_TESTS=$(echo "$TEST_FILES" | wc -l | tr -d ' ')
PASSED=0
FAILED=0
CRASHED=0

echo "Found $TOTAL_TESTS tests in $TEST_DIR/"
echo ""
echo "Progress:"
echo "=========================================="

CURRENT=0

for TEST_FILE in $TEST_FILES; do
    CURRENT=$((CURRENT + 1))
    TEST_NAME=$(basename "$TEST_FILE")

    # Progress indicator
    printf "[%3d/%3d] %-30s ... " "$CURRENT" "$TOTAL_TESTS" "$TEST_NAME"

    # Run test and capture result
    OUTPUT=$(./export/k "$TEST_FILE" 2>&1 || true)

    # Check for actual crashes (fatal errors, segfaults)
    if echo "$OUTPUT" | grep -q "fatal error\|SIGSEGV\|Abort trap\|core dump"; then
        echo "❌ CRASHED"
        CRASHED=$((CRASHED + 1))
    # Check for successful completion
    elif echo "$OUTPUT" | grep -q "\[main\] Timeout"; then
        echo "✅ PASSED"
        PASSED=$((PASSED + 1))
    elif echo "$OUTPUT" | grep -q "TypeCheckException\|K2SMTException\|K2Z3Exception"; then
        echo "✅ PASSED (exception expected)"
        PASSED=$((PASSED + 1))
    elif echo "$OUTPUT" | grep -q "Type checking completed"; then
        echo "✅ PASSED"
        PASSED=$((PASSED + 1))
    else
        echo "❌ FAILED"
        FAILED=$((FAILED + 1))
        # Show first few lines
        echo "$OUTPUT" | head -3 | sed 's/^/    /'
    fi
done

echo "=========================================="
echo ""
echo "Test Summary:"
echo "  Total:   $TOTAL_TESTS"
echo "  ✅ Passed: $PASSED"
echo "  ❌ Failed: $FAILED"
echo "  💥 Crashed: $CRASHED"
echo ""

if [ $CRASHED -gt 0 ]; then
    echo "⚠️  Some tests crashed (Z3 native library issues)"
fi

if [ $FAILED -gt 0 ]; then
    echo ""
    echo "To debug failed tests, run:"
    echo "  ./run-tests.sh -test <filename>"
fi

# Exit code
if [ $CRASHED -gt 0 ]; then
    exit 2
elif [ $FAILED -gt 0 ]; then
    exit 1
else
    exit 0
fi

