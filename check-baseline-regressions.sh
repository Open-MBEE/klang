#!/usr/local/bin/bash

# check-baseline-regressions.sh
# Requires bash 4+ for associative arrays
# Shows status of all tests/examples in a table format, with baseline change tracking.
#
# Usage: ./check-baseline-regressions.sh [options]
#   -v, --verbose    Show more details
#   -h, --help       Show help
#   --table          Show full table of all tests (default)
#   --exceptions     Only show tests with unexpected exceptions
#   --regressions    Only show tests that regressed (baseline changed)
#   --short          Short output (just counts)

set -e
cd "$(dirname "$0")"

# Default settings
VERBOSE=false
SHOW_HELP=false
MODE="table"  # table, exceptions, regressions, short

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--verbose) VERBOSE=true; shift ;;
        -h|--help) SHOW_HELP=true; shift ;;
        --table) MODE="table"; shift ;;
        --exceptions) MODE="exceptions"; shift ;;
        --regressions) MODE="regressions"; shift ;;
        --short) MODE="short"; shift ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

if $SHOW_HELP; then
    cat << 'HELP'
check-baseline-regressions.sh - Test status table with baseline tracking

Usage: ./check-baseline-regressions.sh [options]

Options:
  -v, --verbose    Show more details
  -h, --help       Show this help
  --table          Show full table of all tests (default)
  --exceptions     Only show tests with unexpected exceptions  
  --regressions    Only show tests that regressed
  --short          Summary counts only

Output columns:
  Status      : SAT, UNSAT, TypeCheckErr, K2SMTErr, Crash, Unknown
  Expected    : Yes if file is marked as expected failure
  Baseline    : OK (unchanged), REGRESSED (was passing), NEW (file is new)
  Commit      : Commit hash where baseline changed (if regressed)

Example:
  ./check-baseline-regressions.sh              # Full table
  ./check-baseline-regressions.sh --exceptions # Just broken tests
  ./check-baseline-regressions.sh --short      # Just counts
HELP
    exit 0
fi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Build the project if needed
if [ ! -d "target/classes" ]; then
    echo "Building project..."
    mvn compile -q || { echo "Build failed"; exit 1; }
fi

# Setup Java environment  
if [ -d "$HOME/.sdkman/candidates/java/current" ]; then
    if [ -d "$HOME/.sdkman/candidates/java/current/Contents/Home/bin" ]; then
        export JAVA_HOME="$HOME/.sdkman/candidates/java/current/Contents/Home"
    else
        export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    fi
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Get test files
TEST_FILES=$(find src/tests src/examples -name "*.k" 2>/dev/null | sort)

# Function to check if test is marked as expected to fail
is_expected_failure() {
    local file=$1
    head -10 "$file" 2>/dev/null | grep -qi "should not type check\|should fail\|expected error\|should not pass\|negative example"
}

# Function to run a K file and get detailed status
run_test_detailed() {
    local file=$1
    local output
    # Try with timeout if available, otherwise without
    if command -v timeout &>/dev/null; then
        output=$(timeout 30 ./export/k "$file" 2>&1) || true
    else
        output=$(./export/k "$file" 2>&1) || true
    fi
    
    # Check for different outcomes - be specific about exception type
    if echo "$output" | grep -q "TypeCheckException"; then
        echo "TypeCheckErr"
    elif echo "$output" | grep -q "K2SMTException"; then
        echo "K2SMTErr"
    elif echo "$output" | grep -q "K2Z3Exception"; then
        echo "K2Z3Err"
    elif echo "$output" | grep -q "NullPointerException\|NoSuchElement\|RuntimeException"; then
        echo "Crash"
    elif echo "$output" | grep -q "Exception in thread"; then
        echo "Crash"
    elif echo "$output" | grep -q "UNSAT\|unsatisfiable"; then
        echo "UNSAT"
    elif echo "$output" | grep -q "Top level objects\|No instance variables\|STATISTICS"; then
        echo "SAT"
    else
        echo "Unknown"
    fi
}

# Function to check baseline status (did this test regress?)
# Returns: OK, REGRESSED:<commit>, NEW, or ERROR
check_baseline() {
    local file=$1
    local current_status=$2
    
    # If currently passing (SAT/UNSAT), baseline is OK
    if [[ "$current_status" == "SAT" || "$current_status" == "UNSAT" ]]; then
        echo "OK"
        return
    fi
    
    # If expected failure and getting an error, that's OK
    if is_expected_failure "$file"; then
        echo "OK"
        return
    fi
    
    # Check commits that touched the test file itself (all history, not limited)
    local commits
    commits=$(git log --oneline --format="%H" -- "$file" 2>/dev/null) || true
    
    if [ -z "$commits" ]; then
        echo "NEW"
        return
    fi
    
    # Check each commit to find when the test file change broke it
    for commit in $commits; do
        local prev="${commit}^"
        
        # Check if file existed at previous commit  
        if ! git show "$prev:$file" &>/dev/null 2>&1; then
            continue
        fi
        
        # Get old version and test it with CURRENT compiler
        local old_content old_tmp old_status
        old_content=$(git show "$prev:$file" 2>/dev/null) || continue
        old_tmp=".tmp/baseline_check_$$.k"
        mkdir -p .tmp
        echo "$old_content" > "$old_tmp"
        old_status=$(run_test_detailed "$old_tmp" 2>/dev/null) || old_status="Error"
        rm -f "$old_tmp"
        
        if [[ "$old_status" == "SAT" || "$old_status" == "UNSAT" ]]; then
            # Found regression - this commit to the test file broke it
            local short_commit
            short_commit=$(git log -1 --format="%h" "$commit" 2>/dev/null)
            echo "REGRESSED:$short_commit"
            return
        fi
    done
    
    echo "UNKNOWN"
}

# Collect all test data
declare -A TEST_STATUS
declare -A TEST_EXPECTED
declare -A TEST_BASELINE
declare -A TEST_COMMIT

echo "Analyzing tests..." >&2

total=0
for file in $TEST_FILES; do
    [ ! -f "$file" ] && continue
    total=$((total + 1))
done

count=0
for file in $TEST_FILES; do
    [ ! -f "$file" ] && continue
    count=$((count + 1))
    
    # Progress indicator
    printf "\r[%d/%d] %s                    " "$count" "$total" "$(basename "$file")" >&2
    
    # Get current status
    status=$(run_test_detailed "$file" 2>/dev/null) || status="Error"
    TEST_STATUS[$file]=$status
    
    # Check if expected failure
    if is_expected_failure "$file"; then
        TEST_EXPECTED[$file]="Yes"
    else
        TEST_EXPECTED[$file]="No"
    fi
    
    # Check baseline (only for failures, to save time)
    if [[ "$status" != "SAT" && "$status" != "UNSAT" && "${TEST_EXPECTED[$file]}" == "No" ]]; then
        baseline=$(check_baseline "$file" "$status" 2>/dev/null) || baseline="ERROR"
        if [[ "$baseline" == REGRESSED:* ]]; then
            TEST_BASELINE[$file]="REGRESSED"
            TEST_COMMIT[$file]="${baseline#REGRESSED:}"
        else
            TEST_BASELINE[$file]="$baseline"
            TEST_COMMIT[$file]="-"
        fi
    else
        TEST_BASELINE[$file]="OK"
        TEST_COMMIT[$file]="-"
    fi
done

printf "\r                                                              \r" >&2

# Count by category
SAT_COUNT=0
UNSAT_COUNT=0
TYPECHECK_ERR_COUNT=0
K2SMT_ERR_COUNT=0
K2Z3_ERR_COUNT=0
CRASH_COUNT=0
UNKNOWN_COUNT=0
EXPECTED_FAIL_COUNT=0
REGRESSED_COUNT=0

for file in "${!TEST_STATUS[@]}"; do
    status="${TEST_STATUS[$file]}"
    expected="${TEST_EXPECTED[$file]}"
    baseline="${TEST_BASELINE[$file]}"
    
    case $status in
        SAT) SAT_COUNT=$((SAT_COUNT + 1)) ;;
        UNSAT) UNSAT_COUNT=$((UNSAT_COUNT + 1)) ;;
        TypeCheckErr) 
            if [ "$expected" == "Yes" ]; then
                EXPECTED_FAIL_COUNT=$((EXPECTED_FAIL_COUNT + 1))
            else
                TYPECHECK_ERR_COUNT=$((TYPECHECK_ERR_COUNT + 1))
            fi
            ;;
        K2SMTErr)
            if [ "$expected" == "Yes" ]; then
                EXPECTED_FAIL_COUNT=$((EXPECTED_FAIL_COUNT + 1))
            else
                K2SMT_ERR_COUNT=$((K2SMT_ERR_COUNT + 1))
            fi
            ;;
        K2Z3Err)
            if [ "$expected" == "Yes" ]; then
                EXPECTED_FAIL_COUNT=$((EXPECTED_FAIL_COUNT + 1))
            else
                K2Z3_ERR_COUNT=$((K2Z3_ERR_COUNT + 1))
            fi
            ;;
        Crash)
            if [ "$expected" == "Yes" ]; then
                EXPECTED_FAIL_COUNT=$((EXPECTED_FAIL_COUNT + 1))
            else
                CRASH_COUNT=$((CRASH_COUNT + 1))
            fi
            ;;
        *) UNKNOWN_COUNT=$((UNKNOWN_COUNT + 1)) ;;
    esac
    
    if [ "$baseline" == "REGRESSED" ]; then
        REGRESSED_COUNT=$((REGRESSED_COUNT + 1))
    fi
done

# Output based on mode
case $MODE in
    short)
        echo "======================================"
        echo "Test Summary"
        echo "======================================"
        echo -e "${GREEN}SAT:${NC}             $SAT_COUNT"
        echo -e "${GREEN}UNSAT:${NC}           $UNSAT_COUNT"
        echo -e "${BLUE}Expected Fail:${NC}   $EXPECTED_FAIL_COUNT"
        echo -e "${YELLOW}TypeCheck Err:${NC}   $TYPECHECK_ERR_COUNT"
        echo -e "${YELLOW}K2SMT Err:${NC}       $K2SMT_ERR_COUNT"
        echo -e "${YELLOW}K2Z3 Err:${NC}        $K2Z3_ERR_COUNT"
        echo -e "${RED}Crash:${NC}           $CRASH_COUNT"
        echo -e "${CYAN}Unknown:${NC}         $UNKNOWN_COUNT"
        echo "--------------------------------------"
        echo -e "${RED}REGRESSED:${NC}       $REGRESSED_COUNT"
        echo ""
        TOTAL_PASS=$((SAT_COUNT + UNSAT_COUNT + EXPECTED_FAIL_COUNT))
        TOTAL=$((SAT_COUNT + UNSAT_COUNT + EXPECTED_FAIL_COUNT + TYPECHECK_ERR_COUNT + K2SMT_ERR_COUNT + K2Z3_ERR_COUNT + CRASH_COUNT + UNKNOWN_COUNT))
        echo "Pass rate: $TOTAL_PASS / $TOTAL"
        ;;
        
    exceptions)
        echo "======================================"
        echo "Tests with Unexpected Exceptions"
        echo "======================================"
        printf "%-50s %-12s %-10s %-8s\n" "File" "Status" "Baseline" "Commit"
        printf "%-50s %-12s %-10s %-8s\n" "----" "------" "--------" "------"
        
        for file in $(echo "${!TEST_STATUS[@]}" | tr ' ' '\n' | sort); do
            status="${TEST_STATUS[$file]}"
            expected="${TEST_EXPECTED[$file]}"
            baseline="${TEST_BASELINE[$file]}"
            commit="${TEST_COMMIT[$file]}"
            
            # Only show unexpected failures
            if [[ "$expected" == "No" && "$status" != "SAT" && "$status" != "UNSAT" ]]; then
                shortfile=$(echo "$file" | sed 's|src/||')
                printf "%-50s %-12s %-10s %-8s\n" "$shortfile" "$status" "$baseline" "$commit"
            fi
        done
        
        echo ""
        echo "Total unexpected exceptions: $((TYPECHECK_ERR_COUNT + K2SMT_ERR_COUNT + K2Z3_ERR_COUNT + CRASH_COUNT))"
        echo "Regressions (were passing): $REGRESSED_COUNT"
        ;;
        
    regressions)
        echo "======================================"
        echo "Regressed Tests (were passing, now fail)"
        echo "======================================"
        printf "%-50s %-12s %-8s %-40s\n" "File" "Status" "Commit" "Commit Message"
        printf "%-50s %-12s %-8s %-40s\n" "----" "------" "------" "--------------"
        
        for file in $(echo "${!TEST_STATUS[@]}" | tr ' ' '\n' | sort); do
            baseline="${TEST_BASELINE[$file]}"
            
            if [ "$baseline" == "REGRESSED" ]; then
                status="${TEST_STATUS[$file]}"
                commit="${TEST_COMMIT[$file]}"
                msg=$(git log -1 --format="%s" "$commit" 2>/dev/null | head -c 38)
                shortfile=$(echo "$file" | sed 's|src/||')
                printf "%-50s %-12s %-8s %-40s\n" "$shortfile" "$status" "$commit" "$msg"
            fi
        done
        
        echo ""
        echo "Total regressions: $REGRESSED_COUNT"
        ;;
        
    table|*)
        echo "======================================"
        echo "Test Status Table"
        echo "======================================"
        printf "%-50s %-12s %-8s %-10s %-8s\n" "File" "Status" "Expected" "Baseline" "Commit"
        printf "%-50s %-12s %-8s %-10s %-8s\n" "----" "------" "--------" "--------" "------"
        
        for file in $(echo "${!TEST_STATUS[@]}" | tr ' ' '\n' | sort); do
            status="${TEST_STATUS[$file]}"
            expected="${TEST_EXPECTED[$file]}"
            baseline="${TEST_BASELINE[$file]}"
            commit="${TEST_COMMIT[$file]}"
            
            shortfile=$(echo "$file" | sed 's|src/||')
            
            # Color code based on status
            case $status in
                SAT|UNSAT) color=$GREEN ;;
                TypeCheckErr|K2SMTErr|K2Z3Err)
                    if [ "$expected" == "Yes" ]; then color=$BLUE; else color=$YELLOW; fi
                    ;;
                Crash) color=$RED ;;
                *) color=$CYAN ;;
            esac
            
            # Highlight regressions
            if [ "$baseline" == "REGRESSED" ]; then
                color=$RED
            fi
            
            printf "${color}%-50s %-12s %-8s %-10s %-8s${NC}\n" "$shortfile" "$status" "$expected" "$baseline" "$commit"
        done
        
        echo ""
        echo "======================================"
        echo "Summary"
        echo "======================================"
        echo -e "${GREEN}SAT:${NC}             $SAT_COUNT"
        echo -e "${GREEN}UNSAT:${NC}           $UNSAT_COUNT"
        echo -e "${BLUE}Expected Fail:${NC}   $EXPECTED_FAIL_COUNT"
        echo -e "${YELLOW}TypeCheck Err:${NC}   $TYPECHECK_ERR_COUNT (unexpected)"
        echo -e "${YELLOW}K2SMT Err:${NC}       $K2SMT_ERR_COUNT (unexpected)"
        echo -e "${YELLOW}K2Z3 Err:${NC}        $K2Z3_ERR_COUNT (unexpected)"
        echo -e "${RED}Crash:${NC}           $CRASH_COUNT (unexpected)"
        echo -e "${CYAN}Unknown:${NC}         $UNKNOWN_COUNT"
        echo "--------------------------------------"
        echo -e "${RED}REGRESSED:${NC}       $REGRESSED_COUNT tests were passing but now fail"
        ;;
esac
