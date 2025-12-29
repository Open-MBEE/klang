#!/bin/bash
# Analyze baseline mismatches

cd "$(dirname "$0")"
source setup-java.sh

TESTS="global1.k inheritance1.k inheritance10.k inheritance11.k inheritance12.k inheritance2.k inheritance3.k inheritance4.k inheritance6.k inheritance7.k inheritance9.k nw1.k tc4.k testsets1.k testsets4.k testsets6.k testsmt1.k testsmt10.k testsmt11.k testsmt12.k testsmt13.k testsmt14.k testsmt16.k testsmt17.k testsmt18.k testsmt19.k testsmt2.k testsmt20.k testsmt3.k testsmt4.k testsmt5.k testsmt6.k testsmt7.k testsmt8.k testsmt9.k unsat1.k unsat2.k unsat3.k unsat4.k unsat5.k"

mkdir -p .tmp/baseline-analysis

for TEST in $TESTS; do
    BASELINE_FILE="src/tests/baseline/${TEST}.json"
    if [ -f "$BASELINE_FILE" ]; then
        # Extract SMT from baseline JSON
        python3 -c "
import json
import sys
with open('$BASELINE_FILE') as f:
    data = json.load(f)
    print(data.get('smt', ''))
" > ".tmp/baseline-analysis/${TEST}.baseline.smt"
        
        # Run test to generate current SMT
        ./export/k "src/tests/$TEST" > /dev/null 2>&1
        if [ -f ".tmp/k_smt_model.log" ]; then
            cp ".tmp/k_smt_model.log" ".tmp/baseline-analysis/${TEST}.current.smt"
        fi
        
        # Generate diff
        diff ".tmp/baseline-analysis/${TEST}.baseline.smt" ".tmp/baseline-analysis/${TEST}.current.smt" > ".tmp/baseline-analysis/${TEST}.diff" 2>/dev/null || true
        
        # Analyze diff
        DIFF_LINES=$(wc -l < ".tmp/baseline-analysis/${TEST}.diff" | tr -d ' ')
        
        # Check for specific patterns
        HAS_LOGIC=$(grep -c "set-logic" ".tmp/baseline-analysis/${TEST}.diff" 2>/dev/null || echo 0)
        HAS_NULL=$(grep -c "NULL\$" ".tmp/baseline-analysis/${TEST}.diff" 2>/dev/null || echo 0)
        HAS_XKASSERT=$(grep -c "_xkassert" ".tmp/baseline-analysis/${TEST}.diff" 2>/dev/null || echo 0)
        HAS_SET_SORT=$(grep -c "define-sort Set" ".tmp/baseline-analysis/${TEST}.diff" 2>/dev/null || echo 0)
        HAS_STRING=$(grep -c "String Theory" ".tmp/baseline-analysis/${TEST}.diff" 2>/dev/null || echo 0)
        
        # Count actual constraint changes (not just cosmetic)
        LOGICAL_CHANGES=$(grep -E "^[<>].*\(assert" ".tmp/baseline-analysis/${TEST}.diff" 2>/dev/null | grep -v "_xkassert" | wc -l | tr -d ' ')
        
        echo "$TEST|$DIFF_LINES|$HAS_LOGIC|$HAS_NULL|$HAS_XKASSERT|$HAS_SET_SORT|$HAS_STRING|$LOGICAL_CHANGES"
    fi
done
