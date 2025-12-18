#!/bin/bash
# Benchmark solver performance across test files
# Compares Z3, CVC5, MiniZinc, and BAE (if available)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Configuration
TIMEOUT=30  # seconds per test
RESULTS_FILE=".tmp/solver_benchmark_results.csv"
REPORT_FILE=".tmp/solver_benchmark_report.md"

# Ensure .tmp directory exists
mkdir -p .tmp

# Check for required tools
check_solver() {
    local name=$1
    local check_cmd=$2
    if eval "$check_cmd" > /dev/null 2>&1; then
        echo "✓ $name available"
        return 0
    else
        echo "✗ $name not available"
        return 1
    fi
}

echo "=== Solver Availability ==="
HAS_Z3=true  # Z3 is always available via K
HAS_CVC5=$(check_solver "CVC5" "which cvc5" && echo true || echo false)
HAS_MINIZINC=$(check_solver "MiniZinc" "which minizinc" && echo true || echo false)
HAS_BAE=$(check_solver "BAE" "test -d ~/git/kservices" && echo true || echo false)

echo ""
echo "=== Starting Benchmark ==="
echo ""

# Initialize CSV
echo "file,z3_time,z3_result,cvc5_time,cvc5_result,minizinc_time,minizinc_result,bae_time,bae_result" > "$RESULTS_FILE"

# Function to run a single test with a solver
run_test() {
    local file=$1
    local solver_flag=$2
    local timeout_sec=$3

    local start_time=$(python3 -c "import time; print(time.time())")
    local result
    local exit_code

    if [ -z "$solver_flag" ]; then
        result=$(timeout ${timeout_sec}s ./export/k "$file" 2>&1) || exit_code=$?
    else
        result=$(timeout ${timeout_sec}s ./export/k $solver_flag "$file" 2>&1) || exit_code=$?
    fi

    local end_time=$(python3 -c "import time; print(time.time())")
    local elapsed=$(python3 -c "print(f'{$end_time - $start_time:.3f}')")

    # Determine result status
    local status="unknown"
    if echo "$result" | grep -q "SAT"; then
        status="sat"
    elif echo "$result" | grep -q "UNSAT"; then
        status="unsat"
    elif [ "$exit_code" = "124" ]; then
        status="timeout"
        elapsed="timeout"
    elif echo "$result" | grep -qi "error\|exception"; then
        status="error"
    fi

    echo "${elapsed},${status}"
}

# Get list of test files
TEST_FILES=$(find src/tests src/test src/examples -name "*.k" 2>/dev/null | sort | head -100)

# Count files
TOTAL=$(echo "$TEST_FILES" | wc -l | tr -d ' ')
CURRENT=0

for file in $TEST_FILES; do
    CURRENT=$((CURRENT + 1))
    filename=$(basename "$file")

    printf "[%3d/%3d] %-40s " "$CURRENT" "$TOTAL" "$filename"

    # Z3 (default)
    z3_result=$(run_test "$file" "" "$TIMEOUT")
    z3_time=$(echo "$z3_result" | cut -d',' -f1)
    z3_status=$(echo "$z3_result" | cut -d',' -f2)

    # CVC5
    if [ "$HAS_CVC5" = "true" ]; then
        cvc5_result=$(run_test "$file" "-cvc5" "$TIMEOUT")
        cvc5_time=$(echo "$cvc5_result" | cut -d',' -f1)
        cvc5_status=$(echo "$cvc5_result" | cut -d',' -f2)
    else
        cvc5_time="n/a"
        cvc5_status="n/a"
    fi

    # MiniZinc
    if [ "$HAS_MINIZINC" = "true" ]; then
        minizinc_result=$(run_test "$file" "-minizinc" "$TIMEOUT")
        minizinc_time=$(echo "$minizinc_result" | cut -d',' -f1)
        minizinc_status=$(echo "$minizinc_result" | cut -d',' -f2)
    else
        minizinc_time="n/a"
        minizinc_status="n/a"
    fi

    # BAE (placeholder - implement when BAE integration is ready)
    bae_time="n/a"
    bae_status="n/a"

    # Print summary for this file
    printf "Z3:%-8s CVC5:%-8s MZN:%-8s\n" "$z3_time" "$cvc5_time" "$minizinc_time"

    # Append to CSV
    echo "$file,$z3_time,$z3_status,$cvc5_time,$cvc5_status,$minizinc_time,$minizinc_status,$bae_time,$bae_status" >> "$RESULTS_FILE"
done

echo ""
echo "=== Generating Report ==="

# Generate markdown report
python3 << 'PYTHON_SCRIPT'
import csv
import sys

results_file = ".tmp/solver_benchmark_results.csv"
report_file = ".tmp/solver_benchmark_report.md"

# Read results
results = []
with open(results_file, 'r') as f:
    reader = csv.DictReader(f)
    for row in reader:
        results.append(row)

# Generate report
with open(report_file, 'w') as f:
    f.write("# Solver Benchmark Report\n\n")
    f.write(f"Total files tested: {len(results)}\n\n")

    # Summary table
    f.write("## Performance Summary\n\n")
    f.write("| File | Z3 | CVC5 | MiniZinc | BAE | Recommended |\n")
    f.write("|------|----|----- |----------|-----|-------------|\n")

    recommendations = []

    for row in results:
        file = row['file'].split('/')[-1]  # Just filename
        z3 = row['z3_time']
        cvc5 = row['cvc5_time']
        mzn = row['minizinc_time']
        bae = row['bae_time']

        # Determine recommendation (only if significantly faster)
        recommended = "z3"
        z3_num = float(z3) if z3 not in ['timeout', 'n/a', 'error'] else 999
        cvc5_num = float(cvc5) if cvc5 not in ['timeout', 'n/a', 'error'] else 999
        mzn_num = float(mzn) if mzn not in ['timeout', 'n/a', 'error'] else 999

        # Only recommend if >2x faster than Z3
        if cvc5_num < z3_num / 2 and cvc5_num < mzn_num:
            recommended = "cvc5"
        elif mzn_num < z3_num / 2 and mzn_num < cvc5_num:
            recommended = "minizinc"
        else:
            recommended = "-"  # Z3 is fine or best

        if recommended != "-":
            recommendations.append((row['file'], recommended, z3_num, cvc5_num if cvc5 != 'n/a' else None, mzn_num if mzn != 'n/a' else None))

        f.write(f"| {file} | {z3}s | {cvc5}s | {mzn}s | {bae}s | {recommended} |\n")

    # Files needing annotation
    f.write("\n## Files to Annotate\n\n")
    f.write("These files have a solver that is >2x faster than Z3:\n\n")

    if recommendations:
        f.write("| File | Recommended Solver | Z3 Time | Alt Time | Speedup |\n")
        f.write("|------|-------------------|---------|----------|--------|\n")
        for filepath, solver, z3_t, cvc5_t, mzn_t in recommendations:
            alt_t = cvc5_t if solver == "cvc5" else mzn_t
            speedup = z3_t / alt_t if alt_t and alt_t > 0 else 0
            f.write(f"| {filepath} | {solver} | {z3_t:.3f}s | {alt_t:.3f}s | {speedup:.1f}x |\n")
    else:
        f.write("No files found where alternative solvers significantly outperform Z3.\n")

print(f"Report written to {report_file}")
PYTHON_SCRIPT

echo ""
echo "Results saved to: $RESULTS_FILE"
echo "Report saved to: $REPORT_FILE"
echo ""
cat "$REPORT_FILE"

