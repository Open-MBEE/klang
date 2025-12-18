#!/bin/bash

# Solver Comparison Script for K Language
# Runs tests through both Z3 and CVC5 and compares performance

set -e

cd "$(dirname "$0")"

# Setup Java - prefer ARM64 Java on Apple Silicon
if [ -d "$HOME/.sdkman/candidates/java/21.0.5-tem/Contents/Home" ]; then
    export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.5-tem/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -d "$HOME/.sdkman/candidates/java/current" ]; then
    if [ -d "$HOME/.sdkman/candidates/java/current/Contents/Home/bin" ]; then
        export JAVA_HOME="$HOME/.sdkman/candidates/java/current/Contents/Home"
    else
        export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    fi
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Configure Z3 architecture (silently)
if [ -f "./select-z3-architecture.sh" ]; then
    ./select-z3-architecture.sh > /dev/null 2>&1 || true
fi

# Check if compiled
if [ ! -d "target/classes" ]; then
    echo "ERROR: Project not compiled. Run ./compile.sh first."
    exit 1
fi

# Check if CVC5 is available
CVC5_PATH=""
for path in "export/lib/cvc5" "/usr/local/bin/cvc5" "/opt/homebrew/bin/cvc5" "cvc5"; do
    if [ -x "$path" ] 2>/dev/null || command -v "$path" &>/dev/null; then
        CVC5_PATH="$path"
        break
    fi
done

if [ -z "$CVC5_PATH" ]; then
    echo "WARNING: CVC5 not found. Only Z3 results will be shown."
    echo "         To install CVC5, download from: https://github.com/cvc5/cvc5/releases"
    echo ""
    CVC5_AVAILABLE=false
else
    CVC5_AVAILABLE=true
    echo "Found CVC5 at: $CVC5_PATH"
    echo ""
    echo "NOTE: K's SMT output uses Z3-specific syntax (parametric datatypes)."
    echo "      CVC5 may report ERROR for most tests until SMT output is made"
    echo "      compatible. Direct SMT-LIB2 tests (without K datatypes) work well."
fi

# Default values
TEST_DIRS="src/tests"
FILTER=""
VERBOSE=false
OUTPUT_CSV=false
CSV_FILE=".tmp/solver_comparison.csv"

show_help() {
    echo "======================================"
    echo "K Language Solver Comparison"
    echo "======================================"
    echo ""
    echo "Compares Z3 and CVC5 solver performance on K tests."
    echo ""
    echo "Usage: ./compare-solvers.sh [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -all          Run all tests from all directories"
    echo "  -tests        Run core tests only (src/tests/)"
    echo "  -examples     Run example files (src/examples/)"
    echo "  -filter <pat> Run only tests matching pattern"
    echo "  -csv          Output results to CSV file"
    echo "  -v, --verbose Show detailed output"
    echo "  -h, --help    Show this help"
    echo ""
    echo "Examples:"
    echo "  ./compare-solvers.sh                # Compare on core tests"
    echo "  ./compare-solvers.sh -filter string # Compare on string tests"
    echo "  ./compare-solvers.sh -csv           # Output to CSV"
    echo ""
    exit 0
}

# Parse command line options
while [[ $# -gt 0 ]]; do
    case $1 in
        -all)
            TEST_DIRS="src/tests src/test src/examples"
            shift
            ;;
        -tests)
            TEST_DIRS="src/tests"
            shift
            ;;
        -examples)
            TEST_DIRS="src/examples"
            shift
            ;;
        -filter)
            FILTER="$2"
            shift 2
            ;;
        -csv)
            OUTPUT_CSV=true
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -h|--help)
            show_help
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use -h for help"
            exit 1
            ;;
    esac
done

# Collect test files
TEST_FILES=""
for DIR in $TEST_DIRS; do
    if [ -d "$DIR" ]; then
        if [ -n "$FILTER" ]; then
            DIR_FILES=$(find "$DIR" -maxdepth 1 -name "*.k" 2>/dev/null | grep -i "$FILTER" || true)
        else
            DIR_FILES=$(find "$DIR" -maxdepth 1 -name "*.k" 2>/dev/null || true)
        fi
        TEST_FILES="$TEST_FILES $DIR_FILES"
    fi
done

TEST_FILES=$(echo "$TEST_FILES" | tr ' ' '\n' | grep -v '^$' | sort)
TOTAL_TESTS=$(echo "$TEST_FILES" | grep -c . || echo 0)

if [ "$TOTAL_TESTS" = "0" ]; then
    echo "No test files found!"
    exit 1
fi

echo ""
echo "======================================"
echo "Solver Comparison: Z3 vs CVC5"
echo "======================================"
echo "Tests: $TOTAL_TESTS"
echo "Directories: $TEST_DIRS"
if [ -n "$FILTER" ]; then
    echo "Filter: $FILTER"
fi
echo ""

# Create temp directory
mkdir -p .tmp

# Initialize CSV if needed
if [ "$OUTPUT_CSV" = true ]; then
    echo "test,z3_time,z3_result,cvc5_time,cvc5_result,speedup" > "$CSV_FILE"
fi

# Results tracking
Z3_TOTAL_TIME=0
CVC5_TOTAL_TIME=0
Z3_SAT=0
Z3_UNSAT=0
CVC5_SAT=0
CVC5_UNSAT=0
SAME_RESULT=0
DIFF_RESULT=0
CVC5_FASTER=0
Z3_FASTER=0

printf "%-40s | %-15s | %-15s | %-10s\n" "Test" "Z3" "CVC5" "Speedup"
printf "%s\n" "--------------------------------------------------------------------------------"

CURRENT=0
for TEST_FILE in $TEST_FILES; do
    CURRENT=$((CURRENT + 1))
    TEST_NAME=$(basename "$TEST_FILE")

    # Run with Z3
    Z3_START=$(python3 -c 'import time; print(time.time())' 2>/dev/null || date +%s)
    Z3_OUTPUT=$(./export/k "$TEST_FILE" 2>&1 || true)
    Z3_END=$(python3 -c 'import time; print(time.time())' 2>/dev/null || date +%s)
    Z3_TIME=$(echo "$Z3_END $Z3_START" | awk '{printf "%.3f", $1 - $2}')

    # Parse Z3 result
    if echo "$Z3_OUTPUT" | grep -qi "is satisfiable\|Top level objects created"; then
        Z3_RESULT="SAT"
        Z3_SAT=$((Z3_SAT + 1))
    elif echo "$Z3_OUTPUT" | grep -qi "NOT satisfiable"; then
        Z3_RESULT="UNSAT"
        Z3_UNSAT=$((Z3_UNSAT + 1))
    elif echo "$Z3_OUTPUT" | grep -qi "exception\|UnsatisfiedLinkError"; then
        Z3_RESULT="ERROR"
    else
        Z3_RESULT="UNKNOWN"
    fi

    Z3_TOTAL_TIME=$(echo "$Z3_TOTAL_TIME $Z3_TIME" | awk '{printf "%.3f", $1 + $2}')

    # Run with CVC5 if available
    if [ "$CVC5_AVAILABLE" = true ]; then
        CVC5_START=$(python3 -c 'import time; print(time.time())' 2>/dev/null || date +%s)
        CVC5_OUTPUT=$(./export/k "$TEST_FILE" -cvc5 2>&1 || true)
        CVC5_END=$(python3 -c 'import time; print(time.time())' 2>/dev/null || date +%s)
        CVC5_TIME=$(echo "$CVC5_END $CVC5_START" | awk '{printf "%.3f", $1 - $2}')

        # Parse CVC5 result
        if echo "$CVC5_OUTPUT" | grep -q "is satisfiable\|^sat$"; then
            CVC5_RESULT="SAT"
            CVC5_SAT=$((CVC5_SAT + 1))
        elif echo "$CVC5_OUTPUT" | grep -q "NOT satisfiable\|^unsat$"; then
            CVC5_RESULT="UNSAT"
            CVC5_UNSAT=$((CVC5_UNSAT + 1))
        elif echo "$CVC5_OUTPUT" | grep -q "exception\|Exception\|Error\|Parse Error"; then
            CVC5_RESULT="ERROR"
        else
            CVC5_RESULT="UNKNOWN"
        fi

        CVC5_TOTAL_TIME=$(echo "$CVC5_TOTAL_TIME $CVC5_TIME" | awk '{printf "%.3f", $1 + $2}')

        # Compare results
        if [ "$Z3_RESULT" = "$CVC5_RESULT" ]; then
            SAME_RESULT=$((SAME_RESULT + 1))
        else
            DIFF_RESULT=$((DIFF_RESULT + 1))
        fi

        # Calculate speedup (CVC5 time / Z3 time, so >1 means Z3 faster)
        if [ "$CVC5_TIME" != "0" ] && [ "$Z3_TIME" != "0" ]; then
            SPEEDUP=$(echo "$Z3_TIME $CVC5_TIME" | awk '{if ($2 > 0) printf "%.2fx", $1 / $2; else print "N/A"}')
            if echo "$SPEEDUP" | grep -q "^[0-9]"; then
                SPEEDUP_VAL=$(echo "$SPEEDUP" | sed 's/x//')
                IS_CVC5_FASTER=$(echo "$SPEEDUP_VAL > 1" | bc -l 2>/dev/null || echo 0)
                if [ "$IS_CVC5_FASTER" = "1" ]; then
                    CVC5_FASTER=$((CVC5_FASTER + 1))
                else
                    Z3_FASTER=$((Z3_FASTER + 1))
                fi
            fi
        else
            SPEEDUP="N/A"
        fi

        # Print result line
        printf "%-40s | %6ss %-6s | %6ss %-6s | %s\n" \
            "$TEST_NAME" "$Z3_TIME" "$Z3_RESULT" "$CVC5_TIME" "$CVC5_RESULT" "$SPEEDUP"

        # Write to CSV if enabled
        if [ "$OUTPUT_CSV" = true ]; then
            echo "$TEST_NAME,$Z3_TIME,$Z3_RESULT,$CVC5_TIME,$CVC5_RESULT,$SPEEDUP" >> "$CSV_FILE"
        fi
    else
        # CVC5 not available
        printf "%-40s | %6ss %-6s | %-15s | %s\n" \
            "$TEST_NAME" "$Z3_TIME" "$Z3_RESULT" "N/A" "N/A"
    fi

    # Verbose output
    if [ "$VERBOSE" = true ]; then
        echo "  Z3 output (truncated):"
        echo "$Z3_OUTPUT" | head -5 | sed 's/^/    /'
        if [ "$CVC5_AVAILABLE" = true ]; then
            echo "  CVC5 output (truncated):"
            echo "$CVC5_OUTPUT" | head -5 | sed 's/^/    /'
        fi
        echo ""
    fi
done

echo ""
echo "======================================"
echo "Summary"
echo "======================================"
echo ""
echo "Z3 Results:"
echo "  Total time: ${Z3_TOTAL_TIME}s"
echo "  SAT: $Z3_SAT, UNSAT: $Z3_UNSAT"
echo ""

if [ "$CVC5_AVAILABLE" = true ]; then
    echo "CVC5 Results:"
    echo "  Total time: ${CVC5_TOTAL_TIME}s"
    echo "  SAT: $CVC5_SAT, UNSAT: $CVC5_UNSAT"
    echo ""
    echo "Comparison:"
    echo "  Same result: $SAME_RESULT"
    echo "  Different result: $DIFF_RESULT"
    echo "  CVC5 faster: $CVC5_FASTER tests"
    echo "  Z3 faster: $Z3_FASTER tests"

    # Overall speedup
    if [ "$CVC5_TOTAL_TIME" != "0" ]; then
        OVERALL_SPEEDUP=$(echo "$Z3_TOTAL_TIME $CVC5_TOTAL_TIME" | awk '{if ($2 > 0) printf "%.2fx", $1 / $2; else print "N/A"}')
        echo "  Overall speedup (Z3/CVC5): $OVERALL_SPEEDUP"
    fi
fi

if [ "$OUTPUT_CSV" = true ]; then
    echo ""
    echo "Results written to: $CSV_FILE"
fi

echo ""

