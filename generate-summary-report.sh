#!/bin/bash
# Generate summary HTML report for K language tests
# Outputs to test-report.html in the project root directory
# Usage: ./generate-summary-report.sh

set -e

REPORT_FILE="test-report.html"
DATE=$(date '+%Y-%m-%d %H:%M:%S')

echo "Running all tests..."
TEST_OUTPUT=$(./run-tests.sh 2>&1 || true)

# Extract summary values
TOTAL=$(echo "$TEST_OUTPUT" | grep "Total:" | awk '{print $NF}')
PASSED=$(echo "$TEST_OUTPUT" | grep "Passed:" | awk '{print $NF}')
FAILED=$(echo "$TEST_OUTPUT" | grep "Failed:" | awk '{print $NF}')
PASS_RATE=$(echo "$TEST_OUTPUT" | grep "Pass rate:" | awk '{print $NF}')

# Extract test results (lines with PASSED or FAILED)
TEST_RESULTS=$(echo "$TEST_OUTPUT" | grep -E "^\[.*\].*\.\.\." || true)

# Generate HTML report
cat > "$REPORT_FILE" << EOF
<!DOCTYPE html>
<html>
<head>
<title>K Language Test Report</title>
<style>
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, monospace;
         margin: 40px; background: #1e1e1e; color: #d4d4d4; max-width: 1200px; margin: 0 auto; padding: 40px; }
  h1 { color: #569cd6; border-bottom: 2px solid #569cd6; padding-bottom: 10px; }
  h2 { color: #4ec9b0; margin-top: 30px; }
  .summary { background: #252526; padding: 20px; border-radius: 8px; margin: 20px 0;
             display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; }
  .stat { text-align: center; }
  .stat-value { font-size: 2.5em; font-weight: bold; }
  .stat-label { font-size: 0.9em; color: #888; }
  .pass { color: #4ec9b0; }
  .fail { color: #f14c4c; }
  .date { color: #888; font-size: 0.9em; }
  table { width: 100%; border-collapse: collapse; margin-top: 20px; }
  th, td { text-align: left; padding: 10px; border-bottom: 1px solid #333; }
  th { background: #252526; color: #569cd6; }
  tr:hover { background: #2a2a2a; }
  .status-pass { color: #4ec9b0; font-weight: bold; }
  .status-fail { color: #f14c4c; font-weight: bold; }
  .filter-section { margin: 20px 0; }
  .filter-btn { padding: 8px 16px; margin: 0 5px; border: none; border-radius: 4px; cursor: pointer; }
  .filter-btn.active { background: #569cd6; color: white; }
  .filter-btn:not(.active) { background: #3c3c3c; color: #d4d4d4; }
  .hidden { display: none; }
</style>
<script>
function filterTests(type) {
  const rows = document.querySelectorAll('tbody tr');
  const btns = document.querySelectorAll('.filter-btn');
  btns.forEach(b => b.classList.remove('active'));
  event.target.classList.add('active');
  rows.forEach(row => {
    const status = row.querySelector('td:nth-child(3)').textContent;
    if (type === 'all') {
      row.classList.remove('hidden');
    } else if (type === 'fail') {
      row.classList.toggle('hidden', !status.includes('FAILED'));
    } else if (type === 'pass') {
      row.classList.toggle('hidden', !status.includes('PASSED'));
    }
  });
}
</script>
</head>
<body>
<h1>K Language Test Report</h1>
<p class="date">Generated: $DATE</p>

<div class="summary">
  <div class="stat">
    <div class="stat-value">$TOTAL</div>
    <div class="stat-label">Total Tests</div>
  </div>
  <div class="stat">
    <div class="stat-value pass">$PASSED</div>
    <div class="stat-label">Passed</div>
  </div>
  <div class="stat">
    <div class="stat-value fail">$FAILED</div>
    <div class="stat-label">Failed</div>
  </div>
  <div class="stat">
    <div class="stat-value pass">$PASS_RATE</div>
    <div class="stat-label">Pass Rate</div>
  </div>
</div>

<h2>Test Results</h2>
<div class="filter-section">
  <button class="filter-btn active" onclick="filterTests('all')">All</button>
  <button class="filter-btn" onclick="filterTests('pass')">Passed</button>
  <button class="filter-btn" onclick="filterTests('fail')">Failed</button>
</div>

<table>
<thead>
<tr><th>#</th><th>Test Name</th><th>Status</th><th>Time</th></tr>
</thead>
<tbody>
EOF

# Parse test results and add to table
echo "$TEST_RESULTS" | while read line; do
  if [ -n "$line" ]; then
    # Extract test number, name, status, and time
    num=$(echo "$line" | grep -oE '^\[[[:space:]]*[0-9]+/[0-9]+\]' | tr -d '[]' | cut -d'/' -f1 | tr -d ' ')
    name=$(echo "$line" | sed 's/^\[[^]]*\][[:space:]]*//' | sed 's/\.\.\..*//' | tr -d ' ')

    if echo "$line" | grep -q "PASSED"; then
      status="PASSED"
      status_class="status-pass"
    else
      status="FAILED"
      status_class="status-fail"
    fi

    time=$(echo "$line" | grep -oE '\[[0-9.]+s\]' | tr -d '[]' || echo "-")

    echo "<tr><td>$num</td><td>$name</td><td class=\"$status_class\">$status</td><td>$time</td></tr>" >> "$REPORT_FILE"
  fi
done

cat >> "$REPORT_FILE" << EOF
</tbody>
</table>
</body>
</html>
EOF

echo ""
echo "Report generated: $REPORT_FILE"
echo "  Total: $TOTAL | Passed: $PASSED | Failed: $FAILED | Pass Rate: $PASS_RATE"
