#!/bin/bash
# Generate HTML report comparing batch vs single test execution
# Usage: ./generate-test-report.sh [test-dir]
#   test-dir: "examples", "tests", or "all" (default: "examples")

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

echo "Generating test report for: $DIRS"
echo "This may take a few minutes..."

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
  .col { background: #1e1e1e; padding: 10px; border-radius: 4px; overflow-x: auto; }
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
    } else if (type === 'mismatch') {
      t.classList.toggle('hidden', !t.classList.contains('mismatch'));
    } else if (type === 'fail') {
      t.classList.toggle('hidden', !t.classList.contains('fail'));
    }
  });
}
</script>
</head>
<body>
<h1>K Language Test Report</h1>
<div class="summary">
  <button class="filter-btn active" onclick="filterTests('all')">All Tests</button>
  <button class="filter-btn" onclick="filterTests('mismatch')">Mismatches Only</button>
  <button class="filter-btn" onclick="filterTests('fail')">Failures Only</button>
</div>
HTMLHEAD

# Process each test file
for dir in $DIRS; do
  dir_name=$(basename "$dir")
  echo "<h2>$dir_name</h2>" >> "$REPORT_FILE"

  for kfile in "$dir"/*.k; do
    [ -f "$kfile" ] || continue

    test_name=$(basename "$kfile")
    echo "  Processing $test_name..."

    # Run in single mode (capture output)
    single_output=$(./run-tests.sh -test "$kfile" 2>&1 | tail -30 || true)
    single_result=$(echo "$single_output" | grep -E "^(PASSED|FAILED)" | head -1 || echo "NO RESULT")

    # Extract outcome from single result
    single_outcome=$(echo "$single_result" | sed 's/.*|\([^|]*\)$/\1/' | head -1)

    # Run in batch mode (just get the one-line result)
    batch_output=$(./run-tests.sh -filter "$(basename "$kfile" .k)" 2>&1 | grep "$test_name" || echo "NO RESULT")
    batch_result=$(echo "$batch_output" | grep -E "(PASSED|FAILED)" | head -1 || echo "$batch_output")

    # Determine status
    is_pass=$(echo "$single_result" | grep -c "PASSED" || true)
    is_fail=$(echo "$single_result" | grep -c "FAILED" || true)

    # Check for mismatch between batch and single
    batch_status=$(echo "$batch_result" | grep -oE "(PASSED|FAILED)" | head -1 || echo "?")
    single_status=$(echo "$single_result" | grep -oE "(PASSED|FAILED)" | head -1 || echo "?")
    is_mismatch=0
    [ "$batch_status" != "$single_status" ] && is_mismatch=1

    # Build CSS classes
    css_class="test"
    [ "$is_fail" -gt 0 ] && css_class="$css_class fail"
    [ "$is_mismatch" -gt 0 ] && css_class="$css_class mismatch"

    # Get K source (first 50 lines, HTML escaped)
    k_source=$(head -50 "$kfile" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')

    # Escape outputs for HTML
    single_escaped=$(echo "$single_output" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')
    batch_escaped=$(echo "$batch_result" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')

    # Load analysis notes if they exist (from notes/ directory)
    notes_file="notes/${dir_name}/${test_name%.k}.txt"
    if [ -f "$notes_file" ]; then
      analysis_notes=$(cat "$notes_file" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g')
    else
      analysis_notes="(No analysis yet)"
    fi

    # Write test entry
    cat >> "$REPORT_FILE" << TESTHTML
<div class="$css_class">
  <div class="test-name">$test_name
    <span class="$([ "$is_pass" -gt 0 ] && echo 'pass' || echo 'fail')">[$single_status]</span>
    $([ "$is_mismatch" -gt 0 ] && echo '<span class="mismatch">[BATCH/SINGLE MISMATCH]</span>')
  </div>
  <div class="grid">
    <div class="col">
      <div class="col-header">K Source</div>
      <pre class="source">$k_source</pre>
    </div>
    <div class="col">
      <div class="col-header">Batch Mode Result</div>
      <pre class="batch">$batch_escaped</pre>
    </div>
    <div class="col">
      <div class="col-header">Single Mode Result (last 30 lines)</div>
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
