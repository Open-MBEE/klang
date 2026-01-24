#!/bin/bash
# Generate quick HTML report using batch mode results
# Usage: ./generate-quick-report.sh [examples|tests|all] [OPTIONS]
# Options:
#   --batch-only    Skip single-mode runs (much faster)
#   --reuse-results Skip re-running tests, use existing .tmp/batch_raw_output.txt

set -e

TEST_DIR="${1:-examples}"
BATCH_ONLY=false
REUSE_RESULTS=false

# Parse options
shift 2>/dev/null || true
while [ $# -gt 0 ]; do
  case "$1" in
    --batch-only) BATCH_ONLY=true ;;
    --reuse-results) REUSE_RESULTS=true ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
  shift
done

REPORT_FILE=".tmp/test-report.html"
mkdir -p .tmp

# Determine which directories to test
case "$TEST_DIR" in
  examples) DIRS="src/examples" ;;
  tests) DIRS="src/tests" ;;
  all) DIRS="src/examples src/tests src/test" ;;
  *) echo "Unknown test dir: $TEST_DIR"; exit 1 ;;
esac

echo "Generating quick test report for: $DIRS"

# Start HTML
cat > "$REPORT_FILE" << 'HTMLHEAD'
<!DOCTYPE html>
<html>
<head>
<title>K Language Test Report</title>
<style>
  body { font-family: monospace; margin: 0; background: #1e1e1e; color: #d4d4d4; }
  h1 { color: #569cd6; margin: 0; padding: 10px 20px; }
  h2 { color: #4ec9b0; margin-top: 40px; border-bottom: 1px solid #444; padding-bottom: 5px; }
  .header-fixed { position: sticky; top: 0; background: #1e1e1e; z-index: 100; padding: 10px 20px; border-bottom: 2px solid #444; }
  .content { padding: 20px; }
  .test { margin: 20px 0; padding: 15px; background: #252526; border-radius: 8px; }
  .test-name { font-size: 1.2em; font-weight: bold; color: #dcdcaa; }
  .pass { color: #4ec9b0; }
  .fail { color: #f14c4c; }
  .mismatch { color: #ce9178; }
  .grid { display: grid; grid-template-columns: 1fr 1fr 1fr 1fr 1fr; gap: 10px; margin-top: 10px; }
  .col { background: #1e1e1e; padding: 10px; border-radius: 4px; overflow-x: auto; max-height: 400px; overflow-y: auto; }
  .col-header { font-weight: bold; color: #569cd6; margin-bottom: 5px; }
  pre { margin: 0; white-space: pre-wrap; word-wrap: break-word; font-size: 11px; }
  .source { color: #ce9178; }
  .batch { color: #9cdcfe; }
  .single { color: #b5cea8; }
  .baseline { color: #dcdcaa; }
  .current { color: #9cdcfe; }
  .diff { color: #d4d4d4; }
  .analysis { color: #ffd700; }
  .diff-add { color: #4ec9b0; background: #1e3a1e; }
  .diff-del { color: #f14c4c; background: #3a1e1e; }
  .diff-hdr { color: #569cd6; }
  .view-toggle { margin-left: 20px; }
  .view-3col .output-col { display: none; }
  .view-diff .baseline-col, .view-diff .current-col { display: none; }
  .grid-3col { grid-template-columns: 1fr 1fr 1fr 1fr 1fr 1fr !important; }
  .grid-diff { grid-template-columns: 1fr 1fr 1fr 1fr 1fr !important; }
  .summary { background: #2d2d30; padding: 15px; border-radius: 8px; margin-bottom: 20px; }
  .filter-btn { margin: 5px; padding: 8px 16px; cursor: pointer; border: none; border-radius: 4px; }
  .filter-btn.active { background: #569cd6; color: white; }
  .filter-btn:not(.active) { background: #3c3c3c; color: #d4d4d4; }
  .filter-group { display: inline-block; margin: 0 15px; }
  .filter-group-label { color: #888; margin-right: 5px; }
  .hidden { display: none; }
</style>
<script>
var statusFilter = 'all';
var categoryFilter = 'all';
var viewMode = 'diff';

function updateFilters() {
  const tests = document.querySelectorAll('.test');
  tests.forEach(t => {
    const isPass = !t.classList.contains('fail');
    const isMismatch = t.dataset.mismatch === 'true';
    const category = t.dataset.category;
    
    let showByStatus = statusFilter === 'all' || 
                       (statusFilter === 'pass' && isPass) || 
                       (statusFilter === 'fail' && !isPass) ||
                       (statusFilter === 'mismatch' && isMismatch);
    let showByCategory = categoryFilter === 'all' || category === categoryFilter;
    
    t.classList.toggle('hidden', !(showByStatus && showByCategory));
  });
}

function filterStatus(type) {
  statusFilter = type;
  document.querySelectorAll('.status-btn').forEach(b => b.classList.remove('active'));
  event.target.classList.add('active');
  updateFilters();
}

function filterCategory(type) {
  categoryFilter = type;
  document.querySelectorAll('.category-btn').forEach(b => b.classList.remove('active'));
  event.target.classList.add('active');
  updateFilters();
}

function toggleView(mode) {
  viewMode = mode;
  document.querySelectorAll('.view-btn').forEach(b => b.classList.remove('active'));
  event.target.classList.add('active');
  document.querySelectorAll('.test').forEach(t => {
    t.classList.remove('view-diff', 'view-3col');
    t.classList.add('view-' + mode);
    const grid = t.querySelector('.grid');
    if (grid) {
      grid.classList.remove('grid-diff', 'grid-3col');
      grid.classList.add('grid-' + mode);
    }
  });
}
</script>
</head>
<body>
<div class="header-fixed">
<h1>K Language Test Report</h1>
<div class="summary">
  <span class="filter-group">
    <span class="filter-group-label">Status:</span>
    <button class="filter-btn status-btn active" onclick="filterStatus('all')">All</button>
    <button class="filter-btn status-btn" onclick="filterStatus('fail')">Failures</button>
    <button class="filter-btn status-btn" onclick="filterStatus('pass')">Passing</button>
    <button class="filter-btn status-btn" onclick="filterStatus('mismatch')">Baseline Mismatch</button>
  </span>
  <span class="filter-group">
    <span class="filter-group-label">Category:</span>
    <button class="filter-btn category-btn active" onclick="filterCategory('all')">All</button>
    <button class="filter-btn category-btn" onclick="filterCategory('tests')">Tests</button>
    <button class="filter-btn category-btn" onclick="filterCategory('examples')">Examples</button>
  </span>
  <span class="filter-group view-toggle">
    <span class="filter-group-label">View:</span>
    <button class="filter-btn view-btn active" onclick="toggleView('diff')">Diff Only</button>
    <button class="filter-btn view-btn" onclick="toggleView('3col')">Old/New/Diff</button>
  </span>
</div>
HTMLHEAD

# Run batch mode and capture results
case "$TEST_DIR" in
  examples) BATCH_FLAG="-examples" ;;
  tests) BATCH_FLAG="-tests" ;;
  all) BATCH_FLAG="-all" ;;
esac

# Run batch tests - this saves output to .tmp/batch_raw_output.txt
# Batch output format: PASSED|time|category|name|result or FAILED|time|category|name|result
BATCH_RAW_FILE=".tmp/batch_raw_output.txt"
if [ "$REUSE_RESULTS" = true ]; then
  if [ -f "$BATCH_RAW_FILE" ]; then
    echo "Reusing existing batch results from $BATCH_RAW_FILE"
  else
    echo "Error: No existing batch results found. Run without --reuse-results first."
    exit 1
  fi
else
  echo "Running batch tests for $TEST_DIR..."
  ./run-tests.sh $BATCH_FLAG 2>&1 || true
fi

# Read from the saved batch output file
if [ -f "$BATCH_RAW_FILE" ]; then
  grep -E "^(PASSED|FAILED)\|" "$BATCH_RAW_FILE" > /tmp/batch_results.txt 2>/dev/null || true
else
  echo "" > /tmp/batch_results.txt
fi
BATCH_RESULTS=$(cat /tmp/batch_results.txt)
echo "Captured $(echo "$BATCH_RESULTS" | wc -l | tr -d ' ') batch result lines"

# Calculate stats from batch results
# Note: "tests" matches src/tests/, "test" matches src/test/ - we count both as "Tests"
TOTAL_TESTS=$(echo "$BATCH_RESULTS" | wc -l | tr -d ' ')
PASSED_TESTS=$(echo "$BATCH_RESULTS" | grep -c "^PASSED" || echo "0")
FAILED_TESTS=$(echo "$BATCH_RESULTS" | grep -c "^FAILED" || echo "0")
TESTS_COUNT=$(echo "$BATCH_RESULTS" | grep -E "\|tests?\|" | wc -l | tr -d ' ')
EXAMPLES_COUNT=$(echo "$BATCH_RESULTS" | grep "|examples|" | wc -l | tr -d ' ')
TESTS_PASSED=$(echo "$BATCH_RESULTS" | grep "^PASSED" | grep -E "\|tests?\|" | wc -l | tr -d ' ')
TESTS_FAILED=$(echo "$BATCH_RESULTS" | grep "^FAILED" | grep -E "\|tests?\|" | wc -l | tr -d ' ')
EXAMPLES_PASSED=$(echo "$BATCH_RESULTS" | grep "^PASSED" | grep "|examples|" | wc -l | tr -d ' ')
EXAMPLES_FAILED=$(echo "$BATCH_RESULTS" | grep "^FAILED" | grep "|examples|" | wc -l | tr -d ' ')
if [ "$TOTAL_TESTS" -gt 0 ]; then
  PASS_RATE=$((PASSED_TESTS * 100 / TOTAL_TESTS))
else
  PASS_RATE=0
fi

# Write summary stats into report
cat >> "$REPORT_FILE" << STATSHTML
<div class="summary" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px;">
  <div>
    <h3 style="margin: 0 0 10px 0; color: #569cd6;">Overall</h3>
    <div style="font-size: 1.2em;">
      <span class="pass">✅ $PASSED_TESTS passed</span> /
      <span class="fail">❌ $FAILED_TESTS failed</span>
      <div style="margin-top: 5px; color: #888;">Pass rate: $PASS_RATE%</div>
    </div>
  </div>
  <div>
    <h3 style="margin: 0 0 10px 0; color: #4ec9b0;">Tests ($TESTS_COUNT)</h3>
    <div>
      <span class="pass">✅ $TESTS_PASSED passed</span> /
      <span class="fail">❌ $TESTS_FAILED failed</span>
    </div>
  </div>
  <div>
    <h3 style="margin: 0 0 10px 0; color: #ce9178;">Examples ($EXAMPLES_COUNT)</h3>
    <div>
      <span class="pass">✅ $EXAMPLES_PASSED passed</span> /
      <span class="fail">❌ $EXAMPLES_FAILED failed</span>
    </div>
  </div>
</div>
</div>
<div class="content">
STATSHTML

# Process each test file
for dir in $DIRS; do
  dir_name=$(basename "$dir")
  echo "<h2>$dir_name</h2>" >> "$REPORT_FILE"

  for kfile in "$dir"/*.k; do
    [ -f "$kfile" ] || continue

    test_name=$(basename "$kfile")
    echo "  Processing $test_name..."

    # Get batch result for this test
    # Batch format: STATUS|time|category|name|result
    batch_result=$(echo "$BATCH_RESULTS" | grep "|${test_name}|" | head -1 || echo "")

    # Run single mode to get full output (skip if --batch-only)
    if [ "$BATCH_ONLY" = true ]; then
      single_output="(Skipped - batch-only mode)"
    else
      single_output=$(./run-tests.sh -test "$kfile" 2>&1 || true)
    fi

    # Determine pass/fail
    is_pass=0
    is_fail=0
    if echo "$batch_result" | grep -q "PASSED"; then
      is_pass=1
    elif echo "$batch_result" | grep -q "FAILED"; then
      is_fail=1
    fi

    # Build CSS classes
    css_class="test"
    [ "$is_fail" -gt 0 ] && css_class="$css_class fail"

    # Get K source (first 50 lines, HTML escaped)
    #k_source=$(head -50 "$kfile" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')
    k_source=$(sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g' "$kfile")

    # Escape batch result and single output for HTML
    batch_escaped=$(echo "$batch_result" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')
    single_escaped=$(echo "$single_output" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')

    # Get @expected annotation
    #expected=$(head -20 "$kfile" | grep -E "@expected" | head -1 | sed 's/.*@expected//' | tr -d '[:space:]' || echo "")
    expected=$(grep -E "@expected" "$kfile" | head -1 | sed 's/.*@expected//' | tr -d '[:space:]' || echo "")

    # Load analysis notes if they exist
    notes_file="notes/${dir_name}/${test_name%.k}.txt"
    if [ -f "$notes_file" ]; then
      analysis_notes=$(cat "$notes_file" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')
    else
      analysis_notes="(No analysis yet)"
    fi

    # Check for baseline mismatch info from batch result and get baseline content
    baseline_status="✅ No baseline file"
    baseline_content=""
    current_content=""
    diff_content=""
    baseline_file="${dir}/baseline/${test_name%.k}.k.json"
    if [ -f "$baseline_file" ]; then
      if echo "$batch_result" | grep -q "baseline mismatch"; then
        # Extract what mismatched
        mismatch_info=$(echo "$batch_result" | sed -n 's/.*baseline mismatch: \([^)]*\).*/\1/p')
        baseline_status="⚠️ MISMATCH: $mismatch_info"
        
        # Write test output to temp file for Python to read
        echo "$single_output" > .tmp/current_output.txt
        echo "$batch_result" > .tmp/batch_result.txt
        
        # Get baseline content (old values), current values, and compute diff
        python3 - "$baseline_file" << 'PYEOF' > .tmp/baseline_diff.txt
import json, sys, difflib

baseline_file = sys.argv[1]

def extract_outcome(batch_text, single_text, baseline_outcome):
    """Extract outcome from test output"""
    # Check batch result format first - most reliable
    # Format: STATUS|time|category|name|result
    # result can be "SAT (expected)", "UNSAT (expected)", "baseline mismatch: X", 
    # "got SAT, expected TIMEOUT", etc.
    for line in batch_text.split('\n'):
        if '|' in line:
            parts = line.split('|')
            if len(parts) >= 5:
                result = '|'.join(parts[4:])  # Everything after the 4th pipe
                # Check for "got X, expected Y" format (outcome changed)
                if 'got ' in result:
                    import re
                    m = re.search(r'got\s+(\w+)', result)
                    if m:
                        return m.group(1)
                # Check for baseline mismatch without outcome change
                if 'baseline mismatch: smt' in result and 'outcome' not in result:
                    # Only SMT changed, outcome is same as baseline
                    return baseline_outcome
                if 'baseline mismatch:' in result and 'outcome' in result:
                    # Outcome changed - try to extract from single output
                    pass
                if 'SAT (expected)' in result:
                    return 'SAT'
                if 'UNSAT (expected)' in result:
                    return 'UNSAT'
                if 'ERROR (expected)' in result:
                    return 'ERROR'
                if 'TIMEOUT (expected)' in result:
                    return 'TIMEOUT'
    
    # Check single output for result line
    for line in single_text.split('\n'):
        if 'Result:' in line or 'Outcome:' in line:
            if 'UNSAT' in line:
                return 'UNSAT'
            if 'SAT' in line:
                return 'SAT'
    
    return baseline_outcome  # Default to baseline if we can't determine

try:
    with open(baseline_file) as f:
        baseline = json.load(f)
    
    with open('.tmp/current_output.txt') as f:
        single_output = f.read()
    with open('.tmp/batch_result.txt') as f:
        batch_result = f.read()
    
    # Baseline values - handle ERROR tests (typeChecks=false, no outcome field)
    if baseline.get('typeChecks') == False:
        b_outcome = 'ERROR'
        b_model = '(Type check failure - no model)'
    else:
        b_outcome = baseline.get('outcome', 'N/A')
        b_model = (baseline.get('model') or 'N/A')[:600]
    
    # Current values from output
    c_outcome = extract_outcome(batch_result, single_output, b_outcome)
    
    # Extract current model from single output (look for the object table or SAT result)
    c_model = ""
    if single_output and 'Skipped - batch-only mode' not in single_output:
        lines = single_output.split('\n')
        # Look for the object table (starts with +--------+)
        in_table = False
        table_lines = []
        for line in lines:
            if '+--------+' in line or '|Variable|' in line:
                in_table = True
            if in_table:
                table_lines.append(line)
                if len(table_lines) > 20:
                    break
            # Also look for SAT/UNSAT result lines
            if 'Auto: SAT' in line or 'Auto: UNSAT' in line:
                if not table_lines:
                    table_lines.append(line)
        if table_lines:
            c_model = '\n'.join(table_lines)[:600]
        else:
            # Try to find any SAT/UNSAT indicator
            for line in lines:
                if 'SAT' in line and ('Regular Solver:' in line or 'Auto:' in line):
                    c_model = line.strip()
                    break
            if not c_model:
                c_model = "(see Full Output column)"
    else:
        c_model = "(run without --batch-only to see model)"
    
    # Compute diff summary
    diff_lines = []
    if b_outcome != c_outcome:
        diff_lines.append(f"=== outcome: CHANGED ===")
        diff_lines.append(f"  baseline: {b_outcome}")
        diff_lines.append(f"  current:  {c_outcome}")
    else:
        diff_lines.append(f"=== outcome: {b_outcome} (unchanged) ===")
    
    # Check what changed
    if 'smt' in batch_result and 'mismatch' in batch_result:
        diff_lines.append("")
        diff_lines.append("=== smt: CHANGED ===")
        diff_lines.append("(SMT formula regenerated)")
    
    # Output in sections
    print("===BASELINE===")
    print(f"outcome: {b_outcome}")
    print()
    print("model:")
    print(b_model[:500])
    print("===CURRENT===")
    print(f"outcome: {c_outcome}")
    print()
    print("model:")
    print(c_model)
    print("===DIFF===")
    print('\n'.join(diff_lines))
except Exception as e:
    print(f"===BASELINE===\nError: {e}\n===CURRENT===\n===DIFF===")
PYEOF
        
        # Parse the output into variables
        baseline_content=$(sed -n '/===BASELINE===/,/===CURRENT===/p' .tmp/baseline_diff.txt | grep -v '===BASELINE===' | grep -v '===CURRENT===' || echo "(error)")
        current_content=$(sed -n '/===CURRENT===/,/===DIFF===/p' .tmp/baseline_diff.txt | grep -v '===CURRENT===' | grep -v '===DIFF===' || echo "(error)")
        diff_content=$(sed -n '/===DIFF===/,$p' .tmp/baseline_diff.txt | grep -v '===DIFF===' || echo "(error)")
      elif echo "$batch_result" | grep -qE "baseline OK|ERROR \(expected\)|ERROR \(matches baseline\)|type check failure"; then
        baseline_status="✅ Baseline OK"
        baseline_content=$(python3 -c "
import json
d = json.load(open('$baseline_file'))
# Handle ERROR tests which have typeChecks=false instead of outcome
if d.get('typeChecks') == False:
    print('outcome: ERROR')
    print('model: (Type check failure - no model)')
else:
    print('outcome:', d.get('outcome', 'N/A'))
    print('model:', (d.get('model', '') or 'N/A')[:200])
" 2>/dev/null || echo "(matched)")
        current_content="(matches baseline)"
        diff_content="(no changes)"
      else
        baseline_status="📋 Has baseline (not compared)"
        baseline_content="(exists but not compared)"
        current_content=""
        diff_content=""
      fi
    else
      baseline_content="(No baseline file yet)"
      current_content="(new test)"
      diff_content="(new test - no baseline to compare)"
    fi
    baseline_escaped=$(echo "$baseline_status" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')
    baseline_content_escaped=$(echo "$baseline_content" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')
    current_content_escaped=$(echo "$current_content" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')
    diff_content_escaped=$(echo "$diff_content" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')
    
    # Determine if this is a baseline mismatch for filtering
    is_mismatch="false"
    if echo "$batch_result" | grep -q "baseline mismatch"; then
      is_mismatch="true"
    fi

    # Get status text
    if [ "$is_pass" -gt 0 ]; then
      status_text="PASS"
      status_class="pass"
    elif [ "$is_fail" -gt 0 ]; then
      status_text="FAIL"
      status_class="fail"
    else
      status_text="?"
      status_class=""
    fi

    # Write test entry
    cat >> "$REPORT_FILE" << TESTHTML
<div class="$css_class view-diff" data-category="$dir_name" data-mismatch="$is_mismatch">
  <div class="test-name">$test_name
    <span class="$status_class">[$status_text]</span>
    $([ -n "$expected" ] && echo "<span style=\"color:#888\">@expected: $expected</span>")
    <span style="color:#888; margin-left:10px;">$baseline_escaped</span>
  </div>
  <div class="grid grid-diff">
    <div class="col">
      <div class="col-header">K Source </div>
      <pre class="source">$k_source</pre>
    </div>
    <div class="col">
      <div class="col-header">Batch Result</div>
      <pre class="batch">$batch_escaped</pre>
    </div>
    <div class="col baseline-col">
      <div class="col-header">Baseline (Old)</div>
      <pre class="baseline">$baseline_content_escaped</pre>
    </div>
    <div class="col current-col">
      <div class="col-header">Current (New)</div>
      <pre class="current">$current_content_escaped</pre>
    </div>
    <div class="col diff-col">
      <div class="col-header">Baseline Diff</div>
      <pre class="diff">$diff_content_escaped</pre>
    </div>
    <div class="col output-col">
      <div class="col-header">Full Output</div>
      <pre class="single">$single_escaped</pre>
    </div>
    <div class="col">
      <div class="col-header">Analysis &amp; Notes</div>
      <pre class="analysis">$analysis_notes</pre>
    </div>
  </div>
</div>
TESTHTML
  done
done

# Close HTML
echo "</div></body></html>" >> "$REPORT_FILE"

echo ""
echo "Report generated: $REPORT_FILE"
echo "Open in browser: open $REPORT_FILE"
