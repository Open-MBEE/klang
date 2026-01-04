#!/bin/bash
# Generate quick HTML report using batch mode results
# Usage: ./generate-quick-report.sh [examples|tests|all]

set -e

TEST_DIR="${1:-examples}"
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
  .hidden { display: none; }
</style>
<script>
function filterTests(type) {
  const tests = document.querySelectorAll('.test');
  const btns = document.querySelectorAll('.filter-btn');
  btns.forEach(b => b.classList.remove('active'));
  event.target.classList.add('active');
  tests.forEach(t => {
    if (type === 'all') {
      t.classList.remove('hidden');
    } else if (type === 'fail') {
      t.classList.toggle('hidden', !t.classList.contains('fail'));
    } else if (type === 'pass') {
      t.classList.toggle('hidden', t.classList.contains('fail'));
    }
  });
}
</script>
</head>
<body>
<h1>K Language Test Report</h1>
<div class="summary">
  <button class="filter-btn active" onclick="filterTests('all')">All Tests</button>
  <button class="filter-btn" onclick="filterTests('fail')">Failures Only</button>
  <button class="filter-btn" onclick="filterTests('pass')">Passing Only</button>
</div>
HTMLHEAD

# Run batch mode and capture results
echo "Running batch tests for $TEST_DIR..."
case "$TEST_DIR" in
  examples) BATCH_FLAG="-examples" ;;
  tests) BATCH_FLAG="-tests" ;;
  all) BATCH_FLAG="-all" ;;
esac

# Capture batch results to temp file first for reliability
./run-tests.sh $BATCH_FLAG 2>&1 | grep -E "^\[.*\].*\.\.\." > /tmp/batch_results.txt || true
BATCH_RESULTS=$(cat /tmp/batch_results.txt)
echo "Captured $(echo "$BATCH_RESULTS" | wc -l) batch result lines"

# Process each test file
for dir in $DIRS; do
  dir_name=$(basename "$dir")
  echo "<h2>$dir_name</h2>" >> "$REPORT_FILE"

  for kfile in "$dir"/*.k; do
    [ -f "$kfile" ] || continue

    test_name=$(basename "$kfile")
    echo "  Processing $test_name..."

    # Get batch result for this test (use "] name " pattern to avoid substring matches like k.k matching Bank.k)
    batch_result=$(echo "$BATCH_RESULTS" | grep "] $test_name " | head -1 || echo "")

    # Run single mode to get full output
    #single_output=$(./run-tests.sh -test "$kfile" 2>&1 | tail -40 || true)
    single_output=$(./run-tests.sh -test "$kfile" 2>&1 || true)

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
<div class="$css_class">
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
