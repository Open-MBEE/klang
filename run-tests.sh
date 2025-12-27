#!/bin/bash

# K Language Test Runner
# Runs tests from multiple directories with flexible options

set -e

cd "$(dirname "$0")"
PROJECT_ROOT="$(pwd)"

# Setup Java 21 - handle both Linux and macOS directory structures
source "$PROJECT_ROOT/setup-java.sh"

# Check if compiled
if [ ! -d "target/classes" ]; then
    echo "ERROR: Project not compiled. Run ./compile.sh first."
    exit 1
fi

# Default values
TEST_DIRS="src/tests"
RUN_EXAMPLES=false
RUN_NEW_TESTS=false
RUN_SINGLE_TEST=false
VERBOSE=false
FILTER=""
TEST_FILE=""
PARALLEL_JOBS=1  # Default: batch mode (no parallelism)
TIMING_MODE=false  # Show detailed timing breakdown
SAVE_BASELINE=false  # Save results as baselines

show_help() {
    echo "======================================"
    echo "K Language Test Runner"
    echo "======================================"
    echo ""
    echo "Usage: ./run-tests.sh [OPTIONS]"
    echo ""
    echo "Test Directories:"
    echo "  src/tests/    : Core regression tests (default)"
    echo "  src/test/     : String and advanced solver tests"
    echo "  src/examples/ : Example K files for web application"
    echo ""
    echo "Options:"
    echo "  -all          Run all tests from all directories"
    echo "  -tests        Run core tests only (src/tests/)"
    echo "  -new          Run new feature tests only (src/test/)"
    echo "  -examples     Run example files (src/examples/)"
    echo "  -test <file>  Run a single test file"
    echo "  -filter <pat> Run only tests matching pattern"
    echo "  -timing       Show detailed timing breakdown"
    echo "  -v, --verbose Show full output for each test"
    echo "  -h, --help    Show this help"
    echo ""
    echo "Parallel execution:"
    echo "  -j <N>        Run N tests in parallel (uses shell loop)"
    echo "  -j auto       Auto-detect CPU cores for parallelism"
    echo ""
    echo "Baseline management:"
    echo "  -save-baseline Save current results as new per-file baselines"
    echo "  (Baselines are always checked automatically if they exist)"
    echo ""
    echo "Feature-specific tests:"
    echo "  -opt          Run optimization tests (opt*.k)"
    echo "  -string       Run string operation tests (string*.k)"
    echo "  -regex        Run regex tests (regex*.k)"
    echo ""
    echo "Examples:"
    echo "  ./run-tests.sh              # Run core tests"
    echo "  ./run-tests.sh -all         # Run all tests"
    echo "  ./run-tests.sh -timing      # With detailed timing breakdown"
    echo "  ./run-tests.sh -j 4         # Run with 4 parallel jobs"
    echo "  ./run-tests.sh -j auto      # Auto-detect parallelism"
    echo "  ./run-tests.sh -new         # Run new feature tests"
    echo "  ./run-tests.sh -test opt1.k # Run single test"
    echo "  ./run-tests.sh -filter opt  # Run tests matching 'opt'"
    echo "  ./run-tests.sh -save-baseline # Save current results as baselines"
    echo ""
    echo "Option Precedence:"
    echo "  By default, run-tests.sh uses -prefer-file-options so that"
    echo "  @preferred_options in K files (e.g., -timeout 60000) take"
    echo "  precedence over CLI defaults. This ensures tests run with"
    echo "  their author-intended settings for regression testing."
    echo ""
    echo "Notes:"
    echo "  - Default mode runs all tests in single JVM (fastest)"
    echo "  - @expected annotations in K files define expected outcomes"
    echo "  - Per-file baselines are in baseline/ subdirectories"
    echo "  - Parallel mode (-j) is useful for long-running tests"
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
        -new)
            TEST_DIRS="src/test"
            shift
            ;;
        -examples)
            TEST_DIRS="src/examples"
            shift
            ;;
        -test)
            RUN_SINGLE_TEST=true
            TEST_FILE="$2"
            shift 2
            ;;
        -filter)
            FILTER="$2"
            shift 2
            ;;
        -j)
            if [ "$2" = "auto" ]; then
                # Auto-detect CPU cores
                if [ -f /proc/cpuinfo ]; then
                    PARALLEL_JOBS=$(grep -c ^processor /proc/cpuinfo)
                elif command -v sysctl &> /dev/null; then
                    PARALLEL_JOBS=$(sysctl -n hw.ncpu)
                else
                    PARALLEL_JOBS=4
                fi
            else
                PARALLEL_JOBS="$2"
            fi
            shift 2
            ;;
        -timing)
            TIMING_MODE=true
            shift
            ;;
        -opt)
            FILTER="opt"
            TEST_DIRS="src/tests src/test"
            shift
            ;;
        -string)
            FILTER="string\|String"
            TEST_DIRS="src/tests src/test"
            shift
            ;;
        -regex)
            FILTER="regex"
            TEST_DIRS="src/tests src/test"
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -save-baseline)
            SAVE_BASELINE=true
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

echo "======================================"
echo "K Language Test Runner"
echo "======================================"
echo ""

# Run single test
if [ "$RUN_SINGLE_TEST" = true ]; then
    if [ -z "$TEST_FILE" ]; then
        echo "ERROR: -test requires a filename"
        exit 1
    fi

    # Find the test file
    FOUND_FILE=""
    for DIR in src/tests src/test src/examples; do
        if [ -f "$DIR/$TEST_FILE" ]; then
            FOUND_FILE="$DIR/$TEST_FILE"
            break
        fi
    done

    if [ -z "$FOUND_FILE" ]; then
        # Try as direct path
        if [ -f "$TEST_FILE" ]; then
            FOUND_FILE="$TEST_FILE"
        else
            echo "ERROR: Test file not found: $TEST_FILE"
            echo "Searched in: src/tests, src/test, src/examples"
            exit 1
        fi
    fi

    echo "Running test: $FOUND_FILE"
    echo ""
    
    # Setup classpath (similar to export/k)
    if [ -d "$PROJECT_ROOT/target/classes" ]; then
        CLASSPATH="$PROJECT_ROOT/target/classes"
    else
        CLASSPATH="$PROJECT_ROOT/bin"
    fi
    CLASSPATH="$CLASSPATH:$PROJECT_ROOT/src/grammar/antlr-4.7-complete.jar"
    if [ -f "$PROJECT_ROOT/export/lib/com.microsoft.z3.osx.jar" ]; then
        CLASSPATH="$CLASSPATH:$PROJECT_ROOT/export/lib/com.microsoft.z3.osx.jar"
    elif [ -f "$PROJECT_ROOT/lib/com.microsoft.z3.jar" ]; then
        CLASSPATH="$CLASSPATH:$PROJECT_ROOT/lib/com.microsoft.z3.jar"
    else
        CLASSPATH="$CLASSPATH:$PROJECT_ROOT/export/lib/com.microsoft.z3.jar"
    fi
    CLASSPATH="$CLASSPATH:$PROJECT_ROOT/export/lib/*"
    CLASSPATH="$CLASSPATH:$PROJECT_ROOT/export/lib/scalalib/*"
    CLASSPATH="$CLASSPATH:$PROJECT_ROOT/export/lib/elasticsearch-1.5.0/*"
    
    LIB_PATH="$PROJECT_ROOT/export/lib"
    
    # Build java args for single test (use batch mode for baseline support)
    JAVA_ARGS="-batch -prefer-file-options"
    if [ "$SAVE_BASELINE" = true ]; then
        JAVA_ARGS="$JAVA_ARGS -baseline"
    fi
    
    # Run the test using batch mode for proper baseline handling
    echo "$FOUND_FILE" | java -Djava.library.path="$LIB_PATH" -classpath "$CLASSPATH" k.frontend.Main $JAVA_ARGS
    TEST_EXIT_CODE=$?
    
    # Check baseline after test completes
    echo ""
    echo "=========================================="
    
    # Determine baseline path
    TEST_DIR=$(dirname "$FOUND_FILE")
    TEST_NAME=$(basename "$FOUND_FILE")
    BASELINE_FILE="$TEST_DIR/baseline/$TEST_NAME.json"
    
    if [ -f "$BASELINE_FILE" ]; then
        echo "📋 Baseline: $BASELINE_FILE"
        # Extract smtModel status from baseline (simplified check)
        BASELINE_STATUS=$(grep -o '"smtModel"[[:space:]]*:[[:space:]]*"[^"]*"' "$BASELINE_FILE" 2>/dev/null | head -1 || echo "")
        if [ -n "$BASELINE_STATUS" ]; then
            if echo "$BASELINE_STATUS" | grep -q '""'; then
                echo "   Baseline outcome: TIMEOUT/UNKNOWN (empty smtModel)"
            elif echo "$BASELINE_STATUS" | grep -q '"()"'; then
                echo "   Baseline outcome: UNSAT"
            else
                echo "   Baseline outcome: SAT"
            fi
        fi
    else
        echo "📋 No baseline file found at: $BASELINE_FILE"
    fi
    
    # Check @expected annotation
    EXPECTED=$(head -50 "$FOUND_FILE" 2>/dev/null | grep -E "^[[:space:]]*(//|--|/\*|\*).*@expected" | head -1 | sed 's/.*@expected[[:space:]]*//' | tr '[:lower:]' '[:upper:]' | tr -d '[:space:]')
    if [ -n "$EXPECTED" ]; then
        echo "🎯 @expected: $EXPECTED"
    fi
    
    echo "=========================================="
    exit $TEST_EXIT_CODE
fi


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

# Sort and count
TEST_FILES=$(echo "$TEST_FILES" | tr ' ' '\n' | grep -v '^$' | sort)
TOTAL_TESTS=$(echo "$TEST_FILES" | grep -c . 2>/dev/null || true)
TOTAL_TESTS=${TOTAL_TESTS:-0}

if [ "$TOTAL_TESTS" = "0" ]; then
    echo "No test files found!"
    if [ -n "$FILTER" ]; then
        echo "Filter pattern: $FILTER"
    fi
    echo "Search directories: $TEST_DIRS"
    exit 1
fi

echo "Test directories: $TEST_DIRS"
if [ -n "$FILTER" ]; then
    echo "Filter: $FILTER"
fi
echo "Found $TOTAL_TESTS tests"
if [ "$PARALLEL_JOBS" -gt 1 ]; then
    echo "Mode: parallel ($PARALLEL_JOBS jobs)"
else
    echo "Mode: batch (single JVM)"
fi
if [ "$SAVE_BASELINE" = true ]; then
    echo "Will save baselines after tests"
fi
echo ""
echo "Progress:"
echo "=========================================="

# Record wall-clock start time
WALL_START=$(date +%s.%N 2>/dev/null || date +%s)

# Default mode: batch (single JVM, fastest) - unless -j is specified for parallelism
if [ "$PARALLEL_JOBS" -eq 1 ]; then
    # Get the directory of this script
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

    # Build classpath same as export/k
    CLASSPATH="$SCRIPT_DIR/target/classes"
    CLASSPATH="$CLASSPATH:$SCRIPT_DIR/src/grammar/antlr-4.7-complete.jar"
    CLASSPATH="$CLASSPATH:$SCRIPT_DIR/export/lib/com.microsoft.z3.osx.jar"
    CLASSPATH="$CLASSPATH:$SCRIPT_DIR/export/lib/*"
    CLASSPATH="$CLASSPATH:$SCRIPT_DIR/export/lib/scalalib/*"

    echo "(Running all tests in single JVM with baseline checking...)"

    # Create temp file for batch output
    BATCH_OUTPUT_FILE=$(mktemp)
    trap "rm -f $BATCH_OUTPUT_FILE" EXIT

    # Build Java args - batch mode always checks baselines
    # Use -prefer-file-options so @preferred_options in K files take precedence
    # (e.g., DSN_Pass.k specifies -timeout 60000 which should override default 30s)
    JAVA_ARGS="-batch -prefer-file-options"
    if [ "$TIMING_MODE" = true ]; then
        JAVA_ARGS="$JAVA_ARGS -timing"
    fi
    if [ "$SAVE_BASELINE" = true ]; then
        JAVA_ARGS="$JAVA_ARGS -baseline"  # -baseline in batch mode means save
    fi

    # Run batch mode - pipe test files to Java, capture output
    # Filter to only lines that look like results (STATUS|...)
    echo "$TEST_FILES" | java -Djava.library.path="$SCRIPT_DIR/export/lib" \
        -Djava.awt.headless=true \
        -classpath "$CLASSPATH" \
        k.frontend.Main $JAVA_ARGS 2>&1 | grep -E "^(PASSED|FAILED|NOTFOUND|UNKNOWN|SUMMARY)\|" > "$BATCH_OUTPUT_FILE"

    # Parse and display results
    PASSED=0
    FAILED=0
    BASELINE_MATCHED=0
    BASELINE_MISMATCHED=0
    TOTAL_TIME=0
    CURRENT=0

    while IFS='|' read -r STATUS DURATION TEST_DIR TEST_NAME EXTRA EXTRA2 EXTRA3; do
        if [ "$STATUS" = "SUMMARY" ]; then
            TOTAL_TIME="$DURATION"
            # SUMMARY format: SUMMARY|time|total|passed|failed|baselineMatched|baselineMismatched
            BASELINE_MATCHED="${EXTRA2:-0}"
            BASELINE_MISMATCHED="${EXTRA3:-0}"
            continue
        fi

        CURRENT=$((CURRENT + 1))

        case "$STATUS" in
            PASSED)
                PASSED=$((PASSED + 1))
                if [ -n "$EXTRA" ]; then
                    printf "[%3d/%3d] %-35s ... ✅ PASSED (%s) [%ss]\n" "$CURRENT" "$TOTAL_TESTS" "[$TEST_DIR] $TEST_NAME" "$EXTRA" "$DURATION"
                else
                    printf "[%3d/%3d] %-35s ... ✅ PASSED [%ss]\n" "$CURRENT" "$TOTAL_TESTS" "[$TEST_DIR] $TEST_NAME" "$DURATION"
                fi
                ;;
            FAILED)
                FAILED=$((FAILED + 1))
                printf "[%3d/%3d] %-35s ... ❌ FAILED (%s) [%ss]\n" "$CURRENT" "$TOTAL_TESTS" "[$TEST_DIR] $TEST_NAME" "$EXTRA" "$DURATION"
                ;;
            NOTFOUND)
                FAILED=$((FAILED + 1))
                printf "[%3d/%3d] %-35s ... ❌ NOT FOUND\n" "$CURRENT" "$TOTAL_TESTS" "[$TEST_DIR] $TEST_NAME"
                ;;
            *)
                FAILED=$((FAILED + 1))
                printf "[%3d/%3d] %-35s ... ❓ UNKNOWN (%s) [%ss]\n" "$CURRENT" "$TOTAL_TESTS" "[$TEST_DIR] $TEST_NAME" "$EXTRA" "$DURATION"
                ;;
        esac
    done < "$BATCH_OUTPUT_FILE"

    # Calculate wall time
    WALL_END=$(date +%s.%N 2>/dev/null || date +%s)
    WALL_TIME=$(echo "$WALL_END $WALL_START" | awk '{printf "%.2f", $1 - $2}')

    echo "=========================================="
    echo ""
    echo "Test Summary:"
    echo "  Total:    $TOTAL_TESTS"
    echo "  ✅ Passed:  $PASSED"
    echo "  ❌ Failed:  $FAILED"
    if [ "$BASELINE_MATCHED" -gt 0 ] || [ "$BASELINE_MISMATCHED" -gt 0 ]; then
        echo "  📋 Baseline matched:    $BASELINE_MATCHED"
        echo "  ⚠️  Baseline mismatched: $BASELINE_MISMATCHED"
    fi
    echo "  ⏱️  CPU time: ${TOTAL_TIME}s"
    echo "  🕐 Wall time: ${WALL_TIME}s"

    echo ""
    if [ "$TOTAL_TESTS" -gt 0 ]; then
        PASS_RATE=$((PASSED * 100 / TOTAL_TESTS))
        echo "Pass rate: ${PASS_RATE}%"
    fi
    if [ "$SAVE_BASELINE" = true ]; then
        echo "Baselines saved to baseline/ subdirectories"
    fi
    echo ""

    if [ $FAILED -gt 0 ]; then
        exit 1
    fi
    exit 0
fi

# Create temp directory for parallel results
RESULTS_DIR=$(mktemp -d)
trap "rm -rf $RESULTS_DIR" EXIT

# Run tests (parallel or sequential)
CURRENT=0
if [ "$PARALLEL_JOBS" -gt 1 ]; then
    # Parallel execution using background jobs
    echo "(Running $PARALLEL_JOBS tests in parallel...)"

    # Create a helper script for running each test
    cat > "$RESULTS_DIR/run_one.sh" << 'EOFSCRIPT'
#!/bin/bash
TEST_NUM=$1
TEST_FILE=$2
RESULTS_DIR=$3

TEST_NAME=$(basename "$TEST_FILE")
TEST_DIR=$(dirname "$TEST_FILE" | sed 's|.*/||')
RESULT_FILE="$RESULTS_DIR/result_$(printf '%04d' $TEST_NUM)"

START_TIME=$(date +%s.%N 2>/dev/null || date +%s)

if command -v gtimeout &> /dev/null; then
    OUTPUT=$(gtimeout 60 ./export/k "$TEST_FILE" 2>&1 || true)
elif command -v timeout &> /dev/null; then
    OUTPUT=$(timeout 60 ./export/k "$TEST_FILE" 2>&1 || true)
else
    OUTPUT=$(./export/k "$TEST_FILE" 2>&1 || true)
fi

END_TIME=$(date +%s.%N 2>/dev/null || date +%s)
DURATION=$(echo "$END_TIME $START_TIME" | awk '{printf "%.2f", $1 - $2}')

STATUS="UNKNOWN"
EXTRA=""
ACTUAL_RESULT=""

# Determine actual result
if echo "$OUTPUT" | grep -q "TypeCheckException\|K2SMTException\|K2Z3Exception"; then
    ACTUAL_RESULT="ERROR"
elif echo "$OUTPUT" | grep -q "fatal error\|SIGSEGV\|Abort trap\|core dump"; then
    ACTUAL_RESULT="CRASH"
elif echo "$OUTPUT" | grep -q "\[main\] Timeout"; then
    ACTUAL_RESULT="TIMEOUT"
elif echo "$OUTPUT" | grep -q "model is NOT satisfiable\|NOT satisfiable"; then
    ACTUAL_RESULT="UNSAT"
elif echo "$OUTPUT" | grep -q "Type checking completed\|Top level objects created\|Extra objects created\|STATISTICS"; then
    ACTUAL_RESULT="SAT"
fi

# Check for @expected annotation
EXPECTED=$(head -50 "$TEST_FILE" 2>/dev/null | grep -E "^[[:space:]]*(//|--|/\*|\*).*@expected" | head -1 | sed 's/.*@expected[[:space:]]*//' | tr '[:lower:]' '[:upper:]' | tr -d '[:space:]')

if [ -n "$EXPECTED" ]; then
    # We have an expected result - compare
    if [ "$ACTUAL_RESULT" = "$EXPECTED" ]; then
        STATUS="PASSED"
        EXTRA="$ACTUAL_RESULT (expected)"
    else
        STATUS="FAILED"
        EXTRA="got $ACTUAL_RESULT, expected $EXPECTED"
    fi
else
    # No expected result - use legacy logic
    if [ "$ACTUAL_RESULT" = "ERROR" ]; then
        if head -5 "$TEST_FILE" 2>/dev/null | grep -qi "should not type check\|should fail\|expected error\|should not pass\|negative example"; then
            STATUS="PASSED"; EXTRA="expected type check failure"
        elif echo "$TEST_NAME" | grep -qi "unsat\|error\|fail"; then
            STATUS="PASSED"; EXTRA="expected exception"
        else
            STATUS="PASSED"; EXTRA="exception expected"
        fi
    elif [ "$ACTUAL_RESULT" = "CRASH" ]; then
        STATUS="CRASHED"
    elif [ "$ACTUAL_RESULT" = "SAT" ]; then
        STATUS="PASSED"
    elif [ "$ACTUAL_RESULT" = "UNSAT" ]; then
        STATUS="PASSED"; EXTRA="UNSAT"
    elif [ "$ACTUAL_RESULT" = "TIMEOUT" ]; then
        STATUS="PASSED"; EXTRA="TIMEOUT"
    fi
fi

echo "$STATUS|$DURATION|$TEST_DIR|$TEST_NAME|$EXTRA" > "$RESULT_FILE"
EOFSCRIPT
    chmod +x "$RESULTS_DIR/run_one.sh"

    # Launch tests in batches with controlled parallelism
    TEST_NUM=0
    RUNNING=0
    COMPLETED=0

    # Progress display function
    show_progress() {
        local DONE=$1
        local TOTAL=$2
        local WIDTH=40
        local PERCENT=$((DONE * 100 / TOTAL))
        local FILLED=$((DONE * WIDTH / TOTAL))
        local EMPTY=$((WIDTH - FILLED))
        printf "\r  [%s%s] %d/%d (%d%%)" \
            "$(printf '█%.0s' $(seq 1 $FILLED 2>/dev/null) || echo "")" \
            "$(printf '░%.0s' $(seq 1 $EMPTY 2>/dev/null) || echo "")" \
            "$DONE" "$TOTAL" "$PERCENT"
    }

    # Show initial progress
    show_progress 0 $TOTAL_TESTS

    for TEST_FILE in $TEST_FILES; do
        TEST_NUM=$((TEST_NUM + 1))

        # Run test in background
        "$RESULTS_DIR/run_one.sh" "$TEST_NUM" "$TEST_FILE" "$RESULTS_DIR" &
        RUNNING=$((RUNNING + 1))

        # Wait if we've hit the parallel limit
        if [ "$RUNNING" -ge "$PARALLEL_JOBS" ]; then
            wait -n 2>/dev/null || wait  # wait -n waits for any job (bash 4.3+)
            RUNNING=$((RUNNING - 1))
            COMPLETED=$(ls "$RESULTS_DIR"/result_* 2>/dev/null | wc -l | tr -d ' ')
            show_progress $COMPLETED $TOTAL_TESTS
        fi
    done

    # Wait for all remaining jobs, updating progress
    while [ "$RUNNING" -gt 0 ]; do
        wait -n 2>/dev/null || { wait; break; }
        RUNNING=$((RUNNING - 1))
        COMPLETED=$(ls "$RESULTS_DIR"/result_* 2>/dev/null | wc -l | tr -d ' ')
        show_progress $COMPLETED $TOTAL_TESTS
    done

    # Final progress and newline
    show_progress $TOTAL_TESTS $TOTAL_TESTS
    echo ""
    echo ""

    # Print results in order
    PASSED=0
    FAILED=0
    CRASHED=0
    TOTAL_TIME=0
    SLOW_TESTS=""

    for RESULT_FILE in $(ls "$RESULTS_DIR"/result_* 2>/dev/null | sort); do
        CURRENT=$((CURRENT + 1))
        IFS='|' read -r STATUS DURATION TEST_DIR TEST_NAME EXTRA < "$RESULT_FILE"

        TOTAL_TIME=$(echo "$TOTAL_TIME $DURATION" | awk '{printf "%.2f", $1 + $2}')

        IS_SLOW=$(echo "$DURATION" | awk '{print ($1 > 5.0) ? "yes" : "no"}')
        if [ "$IS_SLOW" = "yes" ]; then
            SLOW_TESTS="$SLOW_TESTS\n  $TEST_NAME: ${DURATION}s"
        fi

        case "$STATUS" in
            PASSED)
                if [ -n "$EXTRA" ]; then
                    printf "[%3d/%3d] %-35s ... ✅ PASSED (%s) [%ss]\n" "$CURRENT" "$TOTAL_TESTS" "[$TEST_DIR] $TEST_NAME" "$EXTRA" "$DURATION"
                else
                    printf "[%3d/%3d] %-35s ... ✅ PASSED [%ss]\n" "$CURRENT" "$TOTAL_TESTS" "[$TEST_DIR] $TEST_NAME" "$DURATION"
                fi
                PASSED=$((PASSED + 1))
                ;;
            CRASHED)
                printf "[%3d/%3d] %-35s ... 💥 CRASHED [%ss]\n" "$CURRENT" "$TOTAL_TESTS" "[$TEST_DIR] $TEST_NAME" "$DURATION"
                CRASHED=$((CRASHED + 1))
                ;;
            *)
                printf "[%3d/%3d] %-35s ... ❓ UNKNOWN [%ss]\n" "$CURRENT" "$TOTAL_TESTS" "[$TEST_DIR] $TEST_NAME" "$DURATION"
                FAILED=$((FAILED + 1))
                ;;
        esac
    done
fi

echo "=========================================="
echo ""

# Calculate wall-clock time
WALL_END=$(date +%s.%N 2>/dev/null || date +%s)
WALL_TIME=$(echo "$WALL_END $WALL_START" | awk '{printf "%.2f", $1 - $2}')

echo "Test Summary:"
echo "  Total:    $TOTAL_TESTS"
echo "  ✅ Passed:  $PASSED"
echo "  ❓ Unknown: $FAILED"
echo "  💥 Crashed: $CRASHED"
echo "  ⏱️  CPU time: ${TOTAL_TIME}s"
echo "  🕐 Wall time: ${WALL_TIME}s"
if [ "$PARALLEL_JOBS" -gt 1 ]; then
    SPEEDUP=$(echo "$TOTAL_TIME $WALL_TIME" | awk '{if ($2 > 0) printf "%.1fx", $1 / $2; else print "N/A"}')
    echo "  🚀 Speedup: $SPEEDUP"
fi
echo ""

# Show slow tests if any
if [ -n "$SLOW_TESTS" ]; then
    echo "⚠️  Slow tests (> 5s):"
    printf "$SLOW_TESTS\n"
    echo ""
fi

if [ "$TOTAL_TESTS" -gt 0 ]; then
    PASS_RATE=$((PASSED * 100 / TOTAL_TESTS))
    echo "Pass rate: ${PASS_RATE}%"
fi
echo ""

if [ $CRASHED -gt 0 ]; then
    echo "⚠️  Some tests crashed (possibly Z3 native library issues)"
fi

if [ $FAILED -gt 0 ]; then
    echo ""
    echo "To debug unknown results, run with -v for verbose output"
    echo "Or run individual test: ./run-tests.sh -test <filename>"
fi

# Exit code
if [ $CRASHED -gt 0 ]; then
    exit 2
elif [ $FAILED -gt 0 ]; then
    exit 1
else
    exit 0
fi

