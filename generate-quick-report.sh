#!/bin/bash
# Generate quick HTML report using batch mode results
# Usage: ./generate-quick-report.sh [examples|tests|all] [--batch-only]
# --batch-only: Skip single-mode runs (much faster)

set -e

TEST_DIR="${1:-examples}"
BATCH_ONLY=false
if [ "$2" = "--batch-only" ]; then
  BATCH_ONLY=true
fi
REPORT_FILE=".tmp/test-report.html"
mkdir -p .tmp

# Determine which directories to test
case "$TEST_DIR" in
  examples) DIRS="src/examples" ;;
  tests) DIRS="src/tests" ;;
  all) DIRS="src/examples src/tests" ;;
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
  body { font-family: monospace; margin: 20px; background: #1e1e1e; color: #d4d4d4; }
  h1 { color: #569cd6; }
  h2 { color: #4ec9b0; margin-top: 40px; border-bottom: 1px solid #444; padding-bottom: 5px; }
  .test { margin: 20px 0; padding: 15px; background: #252526; border-radius: 8px; }
  .test-name { font-size: 1.2em; font-weight: bold; color: #dcdcaa; }
  .pass { color: #4ec9b0; }
  .fail { color: #f14c4c; }
  .mismatch { color: #ce9178; }
  .grid { display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 10px; margin-top: 10px; }
  .col { background: #1e1e1e; padding: 10px; border-radius: 4px; overflow-x: auto; max-height: 400px; }
  .col-header { font-weight: bold; color: #569cd6; margin-bottom: 5px; }
  pre { margin: 0; white-space: pre-wrap; word-wrap: break-word; font-size: 11px; }
  .source { color: #ce9178; }
  .batch { color: #9cdcfe; }
  .single { color: #b5cea8; }
  .analysis { color: #ffd700; }
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

function updateFilters() {
  const tests = document.querySelectorAll('.test');
  tests.forEach(t => {
    const isPass = !t.classList.contains('fail');
    const category = t.dataset.category;
    
    let showByStatus = statusFilter === 'all' || 
                       (statusFilter === 'pass' && isPass) || 
                       (statusFilter === 'fail' && !isPass);
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
</script>
</head>
<body>
<h1>K Language Test Report</h1>
<div class="summary">
  <span class="filter-group">
    <span class="filter-group-label">Status:</span>
    <button class="filter-btn status-btn active" onclick="filterStatus('all')">All</button>
    <button class="filter-btn status-btn" onclick="filterStatus('fail')">Failures</button>
    <button class="filter-btn status-btn" onclick="filterStatus('pass')">Passing</button>
  </span>
  <span class="filter-group">
    <span class="filter-group-label">Category:</span>
    <button class="filter-btn category-btn active" onclick="filterCategory('all')">All</button>
    <button class="filter-btn category-btn" onclick="filterCategory('tests')">Tests</button>
    <button class="filter-btn category-btn" onclick="filterCategory('examples')">Examples</button>
  </span>
</div>
HTMLHEAD

# Run batch mode and capture results
echo "Running batch tests for $TEST_DIR..."
case "$TEST_DIR" in
  examples) BATCH_FLAG="-examples" ;;
  tests) BATCH_FLAG="-tests" ;;
  all) BATCH_FLAG="-all" ;;
esac

# Run batch tests - this saves output to .tmp/batch_raw_output.txt
# Batch output format: PASSED|time|category|name|result or FAILED|time|category|name|result
./run-tests.sh $BATCH_FLAG 2>&1 || true
# Read from the saved batch output file
BATCH_RAW_FILE=".tmp/batch_raw_output.txt"
if [ -f "$BATCH_RAW_FILE" ]; then
  grep -E "^(PASSED|FAILED)\|" "$BATCH_RAW_FILE" > /tmp/batch_results.txt 2>/dev/null || true
else
  echo "" > /tmp/batch_results.txt
fi
BATCH_RESULTS=$(cat /tmp/batch_results.txt)
echo "Captured $(echo "$BATCH_RESULTS" | wc -l | tr -d ' ') batch result lines"

# Calculate stats from batch results
TOTAL_TESTS=$(echo "$BATCH_RESULTS" | wc -l | tr -d ' ')
PASSED_TESTS=$(echo "$BATCH_RESULTS" | grep -c "^PASSED" || echo "0")
FAILED_TESTS=$(echo "$BATCH_RESULTS" | grep -c "^FAILED" || echo "0")
TESTS_COUNT=$(echo "$BATCH_RESULTS" | grep "|tests|" | wc -l | tr -d ' ')
EXAMPLES_COUNT=$(echo "$BATCH_RESULTS" | grep "|examples|" | wc -l | tr -d ' ')
TESTS_PASSED=$(echo "$BATCH_RESULTS" | grep "^PASSED" | grep "|tests|" | wc -l | tr -d ' ')
TESTS_FAILED=$(echo "$BATCH_RESULTS" | grep "^FAILED" | grep "|tests|" | wc -l | tr -d ' ')
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
<div class="$css_class" data-category="$dir_name">
  <div class="test-name">$test_name
    <span class="$status_class">[$status_text]</span>
    $([ -n "$expected" ] && echo "<span style=\"color:#888\">@expected: $expected</span>")
  </div>
  <div class="grid">
    <div class="col">
      <div class="col-header">K Source </div>
      <pre class="source">$k_source</pre>
    </div>
    <div class="col">
      <div class="col-header">Batch Result</div>
      <pre class="batch">$batch_escaped</pre>
    </div>
    <div class="col">
      <div class="col-header">Single Mode Output</div>
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
echo "</body></html>" >> "$REPORT_FILE"

echo ""
echo "Report generated: $REPORT_FILE"
echo "Open in browser: open $REPORT_FILE"
