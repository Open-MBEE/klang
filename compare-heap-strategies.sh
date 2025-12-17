#!/bin/bash
# Compare different heap/solving strategies for K files
# Usage: ./compare-heap-strategies.sh [files...]
#
# If no files given, uses default heap test files

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K_CMD="$SCRIPT_DIR/export/k"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default test files
DEFAULT_FILES="src/tests/cegar1.k src/tests/heap_chain5.k src/tests/heap_linked_list.k src/tests/heap_tree.k"

# Use provided files or defaults
if [ $# -gt 0 ]; then
    FILES="$@"
else
    FILES="$DEFAULT_FILES"
fi

echo "=================================="
echo " Heap Strategy Comparison"
echo "=================================="
echo ""
echo "Files: $FILES"
echo ""

# Function to run a test and capture time
run_test() {
    local file="$1"
    local flag="$2"
    local strategy="$3"

    local start_time=$(python3 -c 'import time; print(int(time.time() * 1000))')

    # Run with background and wait with timeout
    "$K_CMD" $flag "$file" > /tmp/k_result_$$.txt 2>&1 &
    local pid=$!

    # Wait up to 30 seconds
    local count=0
    while kill -0 $pid 2>/dev/null && [ $count -lt 30 ]; do
        sleep 1
        count=$((count + 1))
    done

    if kill -0 $pid 2>/dev/null; then
        kill -9 $pid 2>/dev/null
        wait $pid 2>/dev/null
        result="TIMEOUT"
    else
        wait $pid
        exit_code=$?

        if grep -q "UNSAT" /tmp/k_result_$$.txt 2>/dev/null; then
            result="UNSAT"
        elif grep -q "Exception\|Error" /tmp/k_result_$$.txt 2>/dev/null; then
            result="ERROR"
        elif grep -q "Top level objects" /tmp/k_result_$$.txt 2>/dev/null; then
            result="SAT"
        elif [ $exit_code -eq 0 ]; then
            result="SAT"
        else
            result="ERROR"
        fi
    fi

    local end_time=$(python3 -c 'import time; print(int(time.time() * 1000))')
    local elapsed=$((end_time - start_time))

    # Color based on result
    case $result in
        SAT)
            printf "${GREEN}%-7s${NC}%5dms  " "$result" "$elapsed"
            ;;
        UNSAT)
            printf "${RED}%-7s${NC}%5dms  " "$result" "$elapsed"
            ;;
        TIMEOUT)
            printf "${YELLOW}%-7s${NC}%5dms  " "$result" "$elapsed"
            ;;
        *)
            printf "${RED}%-7s${NC}%5dms  " "$result" "$elapsed"
            ;;
    esac
}

# Print header
printf "%-25s %-12s %-12s %-12s %-12s\n" "File" "unbounded" "bounded" "cegar" "softbounded"
printf "%-25s %-12s %-12s %-12s %-12s\n" "----" "----" "----" "----" "----"

# Test each file with each strategy
for file in $FILES; do
    if [ ! -f "$file" ]; then
        echo "Warning: File not found: $file"
        continue
    fi

    basename=$(basename "$file")
    printf "%-25s " "$basename"

    # Test each strategy
    run_test "$file" "" "unbounded"
    run_test "$file" "-heap" "bounded"
    run_test "$file" "-heapcegar" "cegar"
    run_test "$file" "-heapsoftbounded" "softbounded"

    echo ""
done

echo ""
echo "Legend: SAT=satisfiable, UNSAT=unsatisfiable, TIMEOUT=30s limit, ERROR=solver error"
rm -f /tmp/k_result_$$.txt
