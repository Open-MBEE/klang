#!/usr/bin/env python3
"""
Generate an HTML test report with K model diffs.

This creates a more legible report than generate-quick-report.sh:
- Shows K model output (not SMT) in the diff columns
- Uses colored line-by-line diffs  
- Toggle buttons to show/hide additional columns (SMT, K source, etc.)
- Default view: K model output diff only

Usage: python3 generate-model-diff-report.py [examples|tests|all]
"""

import json
import os
import sys
import subprocess
import difflib
from pathlib import Path
from html import escape as html_escape

def get_test_dirs(test_dir_type):
    """Get directories to test based on type."""
    if test_dir_type == "examples":
        return ["src/examples"]
    elif test_dir_type == "tests":
        return ["src/tests"]
    elif test_dir_type == "all":
        return ["src/examples", "src/tests", "src/test"]
    else:
        print(f"Unknown test dir type: {test_dir_type}")
        sys.exit(1)

def load_baseline(kfile_path):
    """Load baseline JSON for a K file."""
    baseline_dir = os.path.join(os.path.dirname(kfile_path), "baseline")
    baseline_file = os.path.join(baseline_dir, os.path.basename(kfile_path) + ".json")
    if os.path.exists(baseline_file):
        try:
            with open(baseline_file, 'r') as f:
                return json.load(f)
        except Exception as e:
            return None
    return None

def load_expanded_file(kfile_path, suffix):
    """Load an expanded baseline file (e.g., .model.k, .smt, etc.)."""
    base = os.path.basename(kfile_path)
    base_no_ext = base[:-2] if base.endswith('.k') else base
    baseline_dir = os.path.join(os.path.dirname(kfile_path), "baseline")
    
    # Different file naming patterns
    if suffix == '.model.k':
        path = os.path.join(baseline_dir, f"{base_no_ext}{suffix}")
    elif suffix == '.baseline.k':
        path = os.path.join(baseline_dir, f"{base_no_ext}{suffix}")
    else:
        path = os.path.join(baseline_dir, f"{base}{suffix}")
    
    if os.path.exists(path):
        try:
            with open(path, 'r') as f:
                return f.read()
        except:
            return None
    return None

def get_k_source(kfile_path):
    """Read the K source file."""
    try:
        with open(kfile_path, 'r') as f:
            return f.read()
    except:
        return "(error reading file)"

def get_expected(kfile_path):
    """Extract @expected annotation from K file."""
    try:
        with open(kfile_path, 'r') as f:
            for line in f:
                if '@expected' in line:
                    # Extract the expected value (SAT, UNSAT, ERROR, TIMEOUT)
                    parts = line.split('@expected')
                    if len(parts) > 1:
                        value = parts[1].strip().split()[0] if parts[1].strip() else ''
                        return value.strip('(),')
    except:
        pass
    return None

def run_single_test(kfile_path, timeout=60):
    """Run a single K test and capture output."""
    try:
        result = subprocess.run(
            ['./run-tests.sh', '-test', kfile_path],
            capture_output=True,
            text=True,
            timeout=timeout,
            cwd=os.path.dirname(os.path.abspath(__file__)) or '.'
        )
        return result.stdout + result.stderr
    except subprocess.TimeoutExpired:
        return "(TIMEOUT)"
    except Exception as e:
        return f"(ERROR: {e})"

def extract_model_from_output(output):
    """Extract the model/solution table from test output."""
    lines = output.split('\n')
    result = []
    in_table = False
    in_func_interp = False
    
    for line in lines:
        # Detect start of solution table
        if '+--------+' in line or '|Variable|' in line:
            in_table = True
        # Detect function interpretations
        if 'Synthesized function' in line or 'function interpretations' in line:
            in_func_interp = True
        
        if in_table or in_func_interp:
            result.append(line)
            # End of function interpretations
            if in_func_interp and line.strip() == '' and len(result) > 3:
                break
        
        # Also capture SAT/UNSAT result lines
        if 'Auto: SAT' in line or 'Auto: UNSAT' in line:
            if not result:
                result.append(line)
    
    return '\n'.join(result) if result else "(no model in output)"

def colorize_diff(diff_lines):
    """Convert unified diff lines to colored HTML."""
    result = []
    for line in diff_lines:
        escaped = html_escape(line)
        if line.startswith('+++') or line.startswith('---'):
            result.append(f'<span class="diff-hdr">{escaped}</span>')
        elif line.startswith('+'):
            result.append(f'<span class="diff-add">{escaped}</span>')
        elif line.startswith('-'):
            result.append(f'<span class="diff-del">{escaped}</span>')
        elif line.startswith('@@'):
            result.append(f'<span class="diff-hdr">{escaped}</span>')
        else:
            result.append(escaped)
    return '\n'.join(result)

def compute_diff(old_text, new_text, old_label="baseline", new_label="current"):
    """Compute a unified diff between two texts."""
    if old_text is None:
        old_text = ""
    if new_text is None:
        new_text = ""
    
    old_lines = old_text.splitlines(keepends=True)
    new_lines = new_text.splitlines(keepends=True)
    
    diff = list(difflib.unified_diff(old_lines, new_lines, 
                                      fromfile=old_label, tofile=new_label,
                                      lineterm=''))
    
    if not diff:
        return "(no changes)"
    
    return colorize_diff(diff)

def parse_batch_results(batch_file=".tmp/batch_raw_output.txt"):
    """Parse batch test results from file."""
    results = {}
    if os.path.exists(batch_file):
        try:
            with open(batch_file, 'r') as f:
                for line in f:
                    if line.startswith('PASSED|') or line.startswith('FAILED|'):
                        parts = line.strip().split('|')
                        if len(parts) >= 5:
                            status = parts[0]
                            time_s = parts[1]
                            category = parts[2]
                            name = parts[3]
                            info = '|'.join(parts[4:])
                            results[name] = {
                                'status': status,
                                'time': time_s,
                                'category': category,
                                'info': info
                            }
        except:
            pass
    return results

def generate_html_report(test_dir_type, output_file=".tmp/model-diff-report.html", 
                         run_tests=True, batch_only=False):
    """Generate the HTML report."""
    
    test_dirs = get_test_dirs(test_dir_type)
    
    # Run batch tests if requested
    batch_results = {}
    if run_tests:
        print(f"Running batch tests for {test_dir_type}...")
        batch_flag = f"-{test_dir_type}"
        try:
            subprocess.run(['./run-tests.sh', batch_flag], 
                          capture_output=True, timeout=600)
        except:
            pass
        batch_results = parse_batch_results()
    else:
        batch_results = parse_batch_results()
    
    # Collect test files
    test_files = []
    for dir_path in test_dirs:
        if os.path.exists(dir_path):
            for f in sorted(os.listdir(dir_path)):
                if f.endswith('.k'):
                    test_files.append(os.path.join(dir_path, f))
    
    # Statistics
    total = len(test_files)
    passed = sum(1 for f in test_files if batch_results.get(os.path.basename(f), {}).get('status') == 'PASSED')
    failed = sum(1 for f in test_files if batch_results.get(os.path.basename(f), {}).get('status') == 'FAILED')
    
    # Start HTML
    html = []
    html.append('''<!DOCTYPE html>
<html>
<head>
<title>K Language Model Diff Report</title>
<style>
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; 
         margin: 0; background: #1e1e1e; color: #d4d4d4; font-size: 13px; }
  h1 { color: #569cd6; margin: 0; padding: 10px 20px; }
  .header-fixed { position: sticky; top: 0; background: #1e1e1e; z-index: 100; 
                  padding: 10px 20px; border-bottom: 2px solid #444; }
  .content { padding: 20px; }
  .test { margin: 15px 0; padding: 15px; background: #252526; border-radius: 8px; 
          border-left: 4px solid #444; }
  .test.fail { border-left-color: #f14c4c; }
  .test.pass { border-left-color: #4ec9b0; }
  .test-header { display: flex; align-items: center; gap: 15px; margin-bottom: 10px; }
  .test-name { font-size: 1.1em; font-weight: bold; color: #dcdcaa; }
  .status { padding: 2px 8px; border-radius: 4px; font-size: 0.9em; }
  .status.pass { background: #1e3a1e; color: #4ec9b0; }
  .status.fail { background: #3a1e1e; color: #f14c4c; }
  .expected { color: #888; font-size: 0.9em; }
  .grid { display: grid; gap: 10px; margin-top: 10px; }
  .grid-default { grid-template-columns: 1fr 2fr; }
  .grid-all { grid-template-columns: repeat(4, 1fr); }
  .col { background: #1e1e1e; padding: 10px; border-radius: 4px; 
         overflow: auto; max-height: 500px; }
  .col-header { font-weight: bold; color: #569cd6; margin-bottom: 5px; 
                font-size: 0.9em; text-transform: uppercase; }
  pre { margin: 0; white-space: pre-wrap; word-wrap: break-word; 
        font-size: 11px; font-family: 'Menlo', 'Monaco', monospace; }
  .diff-add { color: #4ec9b0; background: #1e3a1e; }
  .diff-del { color: #f14c4c; background: #3a1e1e; }
  .diff-hdr { color: #569cd6; }
  .summary { background: #2d2d30; padding: 15px; border-radius: 8px; 
             margin-bottom: 15px; display: flex; gap: 30px; align-items: center; }
  .stat { text-align: center; }
  .stat-value { font-size: 1.5em; font-weight: bold; }
  .stat-label { font-size: 0.8em; color: #888; }
  .filter-group { display: inline-flex; gap: 5px; margin-left: 20px; }
  .filter-btn { padding: 6px 12px; cursor: pointer; border: none; border-radius: 4px;
                background: #3c3c3c; color: #d4d4d4; font-size: 0.9em; }
  .filter-btn.active { background: #569cd6; color: white; }
  .filter-btn:hover { background: #4a4a4a; }
  .hidden { display: none !important; }
  .col.hidden-col { display: none; }
  .k-source { color: #ce9178; }
  .model-k { color: #9cdcfe; }
  .smt { color: #b5cea8; }
</style>
<script>
var showColumns = {source: false, smt: false, smtModel: false};

function toggleColumn(col) {
  showColumns[col] = !showColumns[col];
  document.querySelectorAll('.col-' + col).forEach(el => {
    el.classList.toggle('hidden-col', !showColumns[col]);
  });
  document.querySelectorAll('.btn-' + col).forEach(btn => {
    btn.classList.toggle('active', showColumns[col]);
  });
  updateGridLayout();
}

function updateGridLayout() {
  const visibleCols = 2 + Object.values(showColumns).filter(v => v).length;
  document.querySelectorAll('.grid').forEach(grid => {
    grid.style.gridTemplateColumns = 'repeat(' + visibleCols + ', 1fr)';
  });
}

function filterStatus(status) {
  document.querySelectorAll('.test').forEach(el => {
    if (status === 'all') {
      el.classList.remove('hidden');
    } else if (status === 'fail') {
      el.classList.toggle('hidden', !el.classList.contains('fail'));
    } else if (status === 'pass') {
      el.classList.toggle('hidden', el.classList.contains('fail'));
    }
  });
  document.querySelectorAll('.status-btn').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.status === status);
  });
}
</script>
</head>
<body>
<div class="header-fixed">
<h1>K Language Model Diff Report</h1>
<div class="summary">
  <div class="stat">
    <div class="stat-value">''' + str(total) + '''</div>
    <div class="stat-label">Total</div>
  </div>
  <div class="stat">
    <div class="stat-value" style="color: #4ec9b0;">''' + str(passed) + '''</div>
    <div class="stat-label">Passed</div>
  </div>
  <div class="stat">
    <div class="stat-value" style="color: #f14c4c;">''' + str(failed) + '''</div>
    <div class="stat-label">Failed</div>
  </div>
  <div class="filter-group">
    <span style="color: #888; margin-right: 5px;">Filter:</span>
    <button class="filter-btn status-btn active" data-status="all" onclick="filterStatus('all')">All</button>
    <button class="filter-btn status-btn" data-status="fail" onclick="filterStatus('fail')">Failed</button>
    <button class="filter-btn status-btn" data-status="pass" onclick="filterStatus('pass')">Passed</button>
  </div>
  <div class="filter-group">
    <span style="color: #888; margin-right: 5px;">Show:</span>
    <button class="filter-btn btn-source" onclick="toggleColumn('source')">K Source</button>
    <button class="filter-btn btn-smt" onclick="toggleColumn('smt')">SMT Input</button>
    <button class="filter-btn btn-smtModel" onclick="toggleColumn('smtModel')">SMT Output</button>
  </div>
</div>
</div>
<div class="content">
''')
    
    # Process each test file
    for kfile in test_files:
        name = os.path.basename(kfile)
        batch_info = batch_results.get(name, {})
        status = batch_info.get('status', 'UNKNOWN')
        info = batch_info.get('info', '')
        expected = get_expected(kfile)
        
        # Load baseline
        baseline = load_baseline(kfile)
        
        # Get baseline model.k content (the K constraints from solution)
        baseline_model_k = load_expanded_file(kfile, '.model.k')
        if baseline_model_k is None and baseline:
            # Fallback to model field in JSON
            baseline_model_k = baseline.get('model', '')
        
        # Get K source
        k_source = get_k_source(kfile)
        
        # Get SMT content
        smt_content = None
        if baseline:
            smt_content = baseline.get('smt', '')
        smt_file_content = load_expanded_file(kfile, '.smt')
        if smt_file_content:
            smt_content = smt_file_content
        
        # Get SMT model
        smt_model_content = None
        if baseline:
            smt_model_content = baseline.get('smtModel', '')
        smt_model_file = load_expanded_file(kfile, '.model.smt')
        if smt_model_file:
            smt_model_content = smt_model_file
        
        # Run single test to get current output (only if not batch-only)
        current_output = ""
        current_model = "(run with --full to see current output)"
        if not batch_only:
            current_output = run_single_test(kfile, timeout=30)
            current_model = extract_model_from_output(current_output)
        
        # Compute diff
        if baseline_model_k:
            model_diff = compute_diff(baseline_model_k, current_model, 
                                      "baseline.model.k", "current output")
        else:
            model_diff = "(no baseline model.k file)"
        
        # CSS classes
        css_class = "test pass" if status == "PASSED" else "test fail"
        status_class = "status pass" if status == "PASSED" else "status fail"
        
        html.append(f'''
<div class="{css_class}">
  <div class="test-header">
    <span class="test-name">{html_escape(name)}</span>
    <span class="{status_class}">{status}</span>
    <span class="expected">@expected: {expected or '-'}</span>
    <span style="color: #888; font-size: 0.85em;">{html_escape(info)}</span>
  </div>
  <div class="grid grid-default">
    <div class="col col-source hidden-col">
      <div class="col-header">K Source</div>
      <pre class="k-source">{html_escape(k_source[:3000]) if k_source else '(none)'}</pre>
    </div>
    <div class="col">
      <div class="col-header">Baseline Model</div>
      <pre class="model-k">{html_escape(baseline_model_k[:2000]) if baseline_model_k else '(no baseline)'}</pre>
    </div>
    <div class="col">
      <div class="col-header">Model Diff (baseline vs current)</div>
      <pre>{model_diff}</pre>
    </div>
    <div class="col col-smt hidden-col">
      <div class="col-header">SMT Input</div>
      <pre class="smt">{html_escape(smt_content[:2000]) if smt_content else '(none)'}</pre>
    </div>
    <div class="col col-smtModel hidden-col">
      <div class="col-header">SMT Model Output</div>
      <pre class="smt">{html_escape(smt_model_content[:2000]) if smt_model_content else '(none)'}</pre>
    </div>
  </div>
</div>
''')
    
    html.append('''
</div>
</body>
</html>
''')
    
    # Write report
    os.makedirs(os.path.dirname(output_file) or '.', exist_ok=True)
    with open(output_file, 'w') as f:
        f.write('\n'.join(html))
    
    print(f"\nReport generated: {output_file}")
    print(f"Open in browser: open {output_file}")
    return output_file

if __name__ == "__main__":
    test_dir = sys.argv[1] if len(sys.argv) > 1 else "examples"
    batch_only = "--batch-only" in sys.argv
    generate_html_report(test_dir, batch_only=batch_only)
