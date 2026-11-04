#!/usr/bin/env python3
"""
Generate a comprehensive HTML test report with 16-column layout.

Layout (16 columns organized as 5 categories + 1 summary):
- K Source: old, new, diff (3 columns)
- K Model Output: old, new, diff (3 columns)
- SMT Input: old, new, diff (3 columns)
- SMT Model Output: old, new, diff (3 columns)
- Whole Output: old, new, diff (3 columns)
- Outcome Summary: expected vs actual, pass/fail, timing (1 column)

Current run outputs are cached in output/ subdirectories for decoupling.

Usage: 
  python3 generate-comprehensive-report.py [examples|tests|all] [--batch-only] [--run-only]
  
  --batch-only: Skip running tests, use cached output if available
  --run-only: Just run tests and cache results, don't generate report
"""

import json
import os
import sys
import subprocess
import difflib
import time
import re
from pathlib import Path
from html import escape as html_escape

# ============================================================================
# Configuration
# ============================================================================

OUTPUT_DIR_NAME = "output"  # Subdirectory name for cached outputs

def get_test_dirs(test_dir_type):
    """Get directories to test based on type."""
    if test_dir_type == "examples":
        return ["src/examples"]
    elif test_dir_type == "tests":
        return ["src/tests"]
    elif test_dir_type == "all":
        return ["src/examples", "src/tests", "src/failing"]
    else:
        print(f"Unknown test dir type: {test_dir_type}")
        sys.exit(1)

# ============================================================================
# File Loading Utilities
# ============================================================================

def load_baseline_json(kfile_path):
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

def load_baseline_file(kfile_path, suffix):
    """Load an expanded baseline file (e.g., .model.k, .smt, etc.)."""
    base = os.path.basename(kfile_path)
    base_no_ext = base[:-2] if base.endswith('.k') else base
    baseline_dir = os.path.join(os.path.dirname(kfile_path), "baseline")
    
    # Different file naming patterns based on how BaselineFileWriter names them
    # .model.k uses: base_no_ext.model.k  (e.g., sm.model.k)
    # .baseline.k uses: base.baseline.k  (e.g., sm.k.baseline.k) 
    # .smt uses: base.smt  (e.g., sm.k.smt)
    # .model.smt uses: base.model.smt  (e.g., sm.k.model.smt)
    if suffix == '.model.k':
        path = os.path.join(baseline_dir, f"{base_no_ext}{suffix}")
    else:
        # .baseline.k, .smt, .model.smt all use base (with .k extension)
        path = os.path.join(baseline_dir, f"{base}{suffix}")
    
    if os.path.exists(path):
        try:
            with open(path, 'r') as f:
                return f.read()
        except:
            return None
    return None

def load_output_file(kfile_path, suffix):
    """Load a file from the output/ directory (cached current run results)."""
    base = os.path.basename(kfile_path)
    base_no_ext = base[:-2] if base.endswith('.k') else base
    output_dir = os.path.join(os.path.dirname(kfile_path), OUTPUT_DIR_NAME)
    
    # Match baseline naming conventions
    # .model.k uses: base_no_ext.model.k
    # Everything else uses: base{suffix}
    if suffix == '.model.k':
        path = os.path.join(output_dir, f"{base_no_ext}{suffix}")
    else:
        path = os.path.join(output_dir, f"{base}{suffix}")
    
    if os.path.exists(path):
        try:
            with open(path, 'r') as f:
                return f.read()
        except:
            return None
    return None

def save_output_file(kfile_path, suffix, content):
    """Save a file to the output/ directory."""
    if content is None:
        return
    base = os.path.basename(kfile_path)
    base_no_ext = base[:-2] if base.endswith('.k') else base
    output_dir = os.path.join(os.path.dirname(kfile_path), OUTPUT_DIR_NAME)
    
    os.makedirs(output_dir, exist_ok=True)
    
    # Match baseline naming conventions
    # .model.k uses: base_no_ext.model.k
    # Everything else uses: base{suffix}
    if suffix == '.model.k':
        path = os.path.join(output_dir, f"{base_no_ext}{suffix}")
    else:
        path = os.path.join(output_dir, f"{base}{suffix}")
    
    try:
        with open(path, 'w') as f:
            f.write(content)
    except Exception as e:
        print(f"Warning: Could not save {path}: {e}")

def get_current_file(filepath):
    """Read current file from disk."""
    try:
        with open(filepath, 'r') as f:
            return f.read()
    except:
        return None

def get_expected(kfile_path):
    """Extract @expected annotation from K file."""
    try:
        with open(kfile_path, 'r') as f:
            for line in f:
                if '@expected' in line:
                    parts = line.split('@expected')
                    if len(parts) > 1:
                        value = parts[1].strip().split()[0] if parts[1].strip() else ''
                        return value.strip('(),')
    except:
        pass
    return None

# ============================================================================
# Test Running
# ============================================================================

def run_single_test(kfile_path, timeout=60):
    """
    Run a single K test and capture all outputs.
    Returns dict with: output, elapsed, actual, smt_input, smt_model
    """
    start = time.time()
    result_data = {
        'output': None,
        'elapsed': 0,
        'actual': 'UNKNOWN',
        'smt_input': None,
        'smt_model': None,
        'model_k': None
    }
    
    try:
        # Run with -debug flag to capture SMT model output
        result = subprocess.run(
            ['./export/k', kfile_path, '-debug'],
            capture_output=True,
            text=True,
            timeout=timeout,
            cwd=os.path.dirname(os.path.abspath(__file__)) or '.'
        )
        elapsed = time.time() - start
        output = result.stdout + result.stderr
        
        result_data['output'] = output
        result_data['elapsed'] = elapsed
        
        # Determine actual outcome from output
        if "Auto: SAT" in output:
            result_data['actual'] = "SAT"
        elif "Auto: UNSAT" in output:
            result_data['actual'] = "UNSAT"
        elif "error" in output.lower() or "Error" in output or "exception" in output.lower():
            result_data['actual'] = "ERROR"
        elif "timeout" in output.lower() or "TIMEOUT" in output:
            result_data['actual'] = "TIMEOUT"
        
        # Extract SMT input from the per-test temp file
        # File is named: .tmp/current-smt-<testname>.smt
        test_name = os.path.basename(kfile_path)
        smt_temp_file = f".tmp/current-smt-{test_name}.smt"
        if os.path.exists(smt_temp_file):
            try:
                with open(smt_temp_file, 'r') as f:
                    result_data['smt_input'] = f.read()
            except:
                pass
        
        # Extract SMT model from output (between smtModel markers if present)
        # The Z3 model is typically printed to console or stored in baseline
        # For now, extract from smtModel section in output if present
        result_data['smt_model'] = extract_smt_model(output)
        
        # Extract K constraints from output (from "Model Constraints" section)
        result_data['model_k'] = extract_k_constraints(output)
        
    except subprocess.TimeoutExpired:
        result_data['output'] = "(TIMEOUT)"
        result_data['elapsed'] = timeout
        result_data['actual'] = "TIMEOUT"
    except Exception as e:
        result_data['output'] = f"(ERROR: {e})"
        result_data['elapsed'] = 0
        result_data['actual'] = "ERROR"
    
    return result_data

def extract_k_constraints(output):
    """
    Extract K constraints from the "Model Constraints" section of output.
    These are in the format: req varname = value
    """
    if not output:
        return None
    
    lines = output.split('\n')
    result = []
    in_constraints = False
    
    for line in lines:
        # Start of constraints section
        if 'Model Constraints' in line or '--- Model Constraints' in line:
            in_constraints = True
            continue
        
        # End markers - stop at @expected, === lines, or new sections
        if in_constraints:
            if '@expected' in line or line.strip().startswith('===') or (line.strip().startswith('---') and 'Constraints' not in line):
                break
            # Skip emoji lines (🎯 etc)
            if any(c in line for c in ['🎯', '📋', '✅', '❌']):
                break
            # Capture constraint lines (starting with req, --, or being comments)
            stripped = line.strip()
            if stripped:
                result.append(stripped)
    
    if result:
        return '\n'.join(result)
    
    # Fallback: try to find any req lines in output
    req_lines = [l.strip() for l in lines if l.strip().startswith('req ')]
    if req_lines:
        return '\n'.join(req_lines)
    
    return None

def extract_smt_model(output):
    """
    Extract SMT model from output if present.
    With -debug flag, the Z3 model is printed after "[K2Z3] DEBUG" prefix.
    """
    if not output:
        return None
    
    lines = output.split('\n')
    model_lines = []
    in_model = False
    
    for line in lines:
        # Start capturing at [K2Z3] DEBUG (define-fun or standalone (define-fun
        if '[K2Z3] DEBUG' in line and '(define-fun' in line:
            # Strip the debug prefix
            idx = line.find('(define-fun')
            in_model = True
            model_lines.append(line[idx:])
        elif in_model:
            # Stop at next debug message or blank line after we have content
            if line.startswith('[') and ']' in line and 'DEBUG' not in line:
                break
            if line.startswith('===') or line.startswith('---'):
                break
            if '(define-fun' in line or line.strip().startswith('(') or line.strip().startswith(')') or line.strip() == '':
                model_lines.append(line)
            elif line.strip() and not line.startswith('['):
                # Continuation of define-fun body
                model_lines.append(line)
            else:
                break
    
    if model_lines:
        # Clean up trailing empty lines
        while model_lines and not model_lines[-1].strip():
            model_lines.pop()
        return '\n'.join(model_lines)
    return None

def extract_model_table(output):
    """Extract the model/solution table from test output (ASCII format)."""
    if not output:
        return None
    
    lines = output.split('\n')
    result = []
    in_table = False
    separator_count = 0
    
    for line in lines:
        # Detect start of solution table (first +--------+ line)
        if '+--------+' in line:
            if not in_table:
                in_table = True
            separator_count += 1
        
        if in_table:
            result.append(line)
            # Table ends after we've seen at least 3 separator lines
            # (header, after column names, footer) and we hit a blank or non-table line
            if separator_count >= 3 and '+--------+' in line:
                break
        
        # Stop at result line if we're past the table
        if in_table and separator_count >= 3 and ('Auto: SAT' in line or 'Auto: UNSAT' in line):
            result.append(line)
            break
    
    # If we found a table, add the SAT/UNSAT result
    if result:
        # Look for SAT/UNSAT result to append
        for line in lines:
            if 'Auto: SAT' in line or 'Auto: UNSAT' in line:
                if line not in result:
                    result.append(line)
                break
        return '\n'.join(result)
    
    # Fallback: look for just SAT/UNSAT result
    for line in lines:
        if 'Auto: SAT' in line or 'Auto: UNSAT' in line:
            return line
    
    return None

def run_and_cache_tests(test_files, timeout=60):
    """Run all tests and cache their outputs."""
    total = len(test_files)
    results = {}
    
    for i, kfile in enumerate(test_files):
        name = os.path.basename(kfile)
        print(f"Running {i+1}/{total}: {name}          ", end='\r')
        
        # Run the test
        run_result = run_single_test(kfile, timeout=timeout)
        
        # Cache the outputs
        save_output_file(kfile, '.output.txt', run_result['output'])
        save_output_file(kfile, '.smt', run_result['smt_input'])
        save_output_file(kfile, '.model.smt', run_result['smt_model'])
        save_output_file(kfile, '.model.k', run_result['model_k'])
        
        # Save metadata as JSON
        metadata = {
            'elapsed': run_result['elapsed'],
            'actual': run_result['actual'],
            'timestamp': time.time()
        }
        save_output_file(kfile, '.meta.json', json.dumps(metadata, indent=2))
        
        results[name] = run_result
    
    print(f"\nCompleted running {total} tests")
    return results

# ============================================================================
# Diff Computation
# ============================================================================

def colorize_diff(diff_lines):
    """Convert unified diff lines to colored HTML."""
    result = []
    for line in diff_lines:
        escaped = html_escape(line.rstrip('\n'))
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

def compute_diff(old_text, new_text, old_label="old", new_label="new"):
    """Compute a unified diff between two texts."""
    if old_text is None:
        old_text = ""
    if new_text is None:
        new_text = ""
    
    # Normalize whitespace for comparison
    old_text = old_text.rstrip()
    new_text = new_text.rstrip()
    
    old_lines = old_text.splitlines(keepends=True)
    new_lines = new_text.splitlines(keepends=True)
    
    diff = list(difflib.unified_diff(old_lines, new_lines, 
                                      fromfile=old_label, tofile=new_label,
                                      lineterm=''))
    
    if not diff:
        return '<span class="no-diff">(identical)</span>'
    
    return colorize_diff(diff)

def truncate_text(text, max_len=4000):
    """Truncate text with indicator."""
    if text is None:
        return ""
    if len(text) > max_len:
        return text[:max_len] + "\n... (truncated)"
    return text

# ============================================================================
# Batch Results Parsing
# ============================================================================

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

# ============================================================================
# HTML Generation
# ============================================================================

def generate_html_header(total, passed, failed):
    """Generate HTML header with CSS and JavaScript."""
    return f'''<!DOCTYPE html>
<html>
<head>
<title>K Language Comprehensive Test Report</title>
<style>
  * {{ box-sizing: border-box; }}
  body {{ 
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; 
    margin: 0; background: #1a1a1a; color: #d4d4d4; font-size: 12px; 
  }}
  h1 {{ color: #569cd6; margin: 0; padding: 8px 15px; font-size: 1.3em; }}
  
  .header-fixed {{ 
    position: sticky; top: 0; background: #1a1a1a; z-index: 100; 
    padding: 8px 15px; border-bottom: 2px solid #444; 
  }}
  
  .summary {{ 
    background: #252526; padding: 10px; border-radius: 6px; 
    margin-bottom: 10px; display: flex; gap: 20px; align-items: center; 
    flex-wrap: wrap;
  }}
  .stat {{ text-align: center; min-width: 60px; }}
  .stat-value {{ font-size: 1.3em; font-weight: bold; }}
  .stat-label {{ font-size: 0.75em; color: #888; }}
  
  .controls {{ display: flex; gap: 15px; flex-wrap: wrap; align-items: center; }}
  .control-group {{ display: flex; align-items: center; gap: 5px; }}
  .control-label {{ color: #888; font-size: 0.85em; }}
  
  .btn {{ 
    padding: 4px 10px; cursor: pointer; border: none; border-radius: 3px;
    background: #3c3c3c; color: #d4d4d4; font-size: 0.85em; 
  }}
  .btn:hover {{ background: #4a4a4a; }}
  .btn.active {{ background: #569cd6; color: white; }}
  
  .content {{ padding: 15px; }}
  
  .test {{ 
    margin: 10px 0; padding: 10px; background: #252526; border-radius: 6px; 
    border-left: 3px solid #444; 
  }}
  .test.fail {{ border-left-color: #f14c4c; }}
  .test.pass {{ border-left-color: #4ec9b0; }}
  
  .test-header {{ 
    display: flex; align-items: center; gap: 10px; margin-bottom: 8px; 
    flex-wrap: wrap;
  }}
  .test-name {{ font-size: 1em; font-weight: bold; color: #dcdcaa; }}
  .status {{ padding: 2px 6px; border-radius: 3px; font-size: 0.85em; }}
  .status.pass {{ background: #1e3a1e; color: #4ec9b0; }}
  .status.fail {{ background: #3a1e1e; color: #f14c4c; }}
  
  /* Category sections */
  .categories {{ display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }}
  
  .category {{ 
    background: #1e1e1e; border-radius: 4px; overflow: hidden;
    flex: 1; min-width: 280px;
  }}
  .category.hidden {{ display: none; }}
  
  .cat-header {{ 
    padding: 6px 10px; background: #2d2d30; 
    font-weight: bold; color: #569cd6; font-size: 0.9em;
    border-bottom: 1px solid #444;
  }}
  
  .cat-content {{ display: flex; gap: 3px; padding: 5px; }}
  
  .sub-col {{ 
    flex: 1; background: #1a1a1a; padding: 5px; border-radius: 3px;
    max-height: 300px; overflow: auto;
  }}
  .sub-col-label {{ 
    font-size: 0.75em; color: #888; margin-bottom: 3px; 
    text-transform: uppercase; 
  }}
  
  pre {{ 
    margin: 0; white-space: pre-wrap; word-wrap: break-word; 
    font-size: 10px; font-family: 'Menlo', 'Monaco', monospace; 
  }}
  
  .diff-add {{ color: #4ec9b0; background: #1e3a1e; }}
  .diff-del {{ color: #f14c4c; background: #3a1e1e; }}
  .diff-hdr {{ color: #569cd6; }}
  .no-diff {{ color: #6a9955; font-style: italic; }}
  
  .k-source {{ color: #ce9178; }}
  .model-k {{ color: #9cdcfe; }}
  .smt {{ color: #b5cea8; }}
  
  .outcome-summary {{ 
    background: #1e1e1e; padding: 0; border-radius: 4px; min-width: 150px;
  }}
  .outcome-row {{ display: flex; justify-content: space-between; margin: 3px 0; }}
  .outcome-label {{ color: #888; }}
  .outcome-value {{ font-weight: bold; }}
  .outcome-match {{ color: #4ec9b0; }}
  .outcome-mismatch {{ color: #f14c4c; }}
  
  .hidden {{ display: none !important; }}
</style>
<script>
// Toggle visibility of categories
var visibleCategories = {{
  kSource: false,
  kModel: true,
  smtInput: false,
  smtModel: true,
  wholeOutput: false
}};

function toggleCategory(cat) {{
  visibleCategories[cat] = !visibleCategories[cat];
  document.querySelectorAll('.cat-' + cat).forEach(el => {{
    el.classList.toggle('hidden', !visibleCategories[cat]);
  }});
  document.querySelectorAll('.btn-' + cat).forEach(btn => {{
    btn.classList.toggle('active', visibleCategories[cat]);
  }});
}}

function filterStatus(status) {{
  document.querySelectorAll('.test').forEach(el => {{
    if (status === 'all') {{
      el.classList.remove('hidden');
    }} else if (status === 'fail') {{
      el.classList.toggle('hidden', !el.classList.contains('fail'));
    }} else if (status === 'pass') {{
      el.classList.toggle('hidden', el.classList.contains('fail'));
    }}
  }});
  document.querySelectorAll('.status-btn').forEach(btn => {{
    btn.classList.toggle('active', btn.dataset.status === status);
  }});
}}

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {{
  // Apply initial visibility
  Object.keys(visibleCategories).forEach(cat => {{
    if (!visibleCategories[cat]) {{
      document.querySelectorAll('.cat-' + cat).forEach(el => {{
        el.classList.add('hidden');
      }});
    }} else {{
      document.querySelectorAll('.btn-' + cat).forEach(btn => {{
        btn.classList.add('active');
      }});
    }}
  }});
}});
</script>
</head>
<body>
<div class="header-fixed">
<h1>K Language Comprehensive Test Report</h1>
<div class="summary">
  <div class="stat">
    <div class="stat-value">{total}</div>
    <div class="stat-label">Total</div>
  </div>
  <div class="stat">
    <div class="stat-value" style="color: #4ec9b0;">{passed}</div>
    <div class="stat-label">Passed</div>
  </div>
  <div class="stat">
    <div class="stat-value" style="color: #f14c4c;">{failed}</div>
    <div class="stat-label">Failed</div>
  </div>
  <div class="controls">
    <div class="control-group">
      <span class="control-label">Filter:</span>
      <button class="btn status-btn active" data-status="all" onclick="filterStatus('all')">All</button>
      <button class="btn status-btn" data-status="fail" onclick="filterStatus('fail')">Failed</button>
      <button class="btn status-btn" data-status="pass" onclick="filterStatus('pass')">Passed</button>
    </div>
    <div class="control-group">
      <span class="control-label">Show:</span>
      <button class="btn btn-kSource" onclick="toggleCategory('kSource')">K Source</button>
      <button class="btn btn-kModel" onclick="toggleCategory('kModel')">K Model</button>
      <button class="btn btn-smtInput" onclick="toggleCategory('smtInput')">SMT Input</button>
      <button class="btn btn-smtModel" onclick="toggleCategory('smtModel')">SMT Output</button>
      <button class="btn btn-wholeOutput" onclick="toggleCategory('wholeOutput')">Full Output</button>
    </div>
  </div>
</div>
</div>
<div class="content">
'''

def generate_category_html(cat_id, cat_name, old_content, new_content, diff_content):
    """Generate HTML for one category (3 sub-columns: old, new, diff)."""
    old_display = html_escape(truncate_text(old_content, 2000)) if old_content else '<span style="color:#666">(none)</span>'
    new_display = html_escape(truncate_text(new_content, 2000)) if new_content else '<span style="color:#666">(none)</span>'
    
    return f'''
    <div class="category cat-{cat_id}">
      <div class="cat-header">{cat_name}</div>
      <div class="cat-content">
        <div class="sub-col">
          <div class="sub-col-label">Old (baseline)</div>
          <pre>{old_display}</pre>
        </div>
        <div class="sub-col">
          <div class="sub-col-label">New (current)</div>
          <pre>{new_display}</pre>
        </div>
        <div class="sub-col">
          <div class="sub-col-label">Diff</div>
          <pre>{diff_content}</pre>
        </div>
      </div>
    </div>'''

def generate_outcome_html(expected, actual, old_time, new_time, pass_fail):
    """Generate HTML for outcome summary column."""
    # Compare expected vs actual
    exp_actual_match = "match" if expected and actual and expected.upper() == actual.upper() else "mismatch"
    if not expected:
        exp_actual_match = "match"  # No expectation means no mismatch
    
    # Format times
    old_time_str = f"{old_time:.2f}s" if old_time else "-"
    new_time_str = f"{new_time:.2f}s" if new_time else "-"
    
    time_change = ""
    if old_time and new_time and old_time > 0:
        ratio = new_time / old_time
        if ratio > 1.5:
            time_change = f' <span style="color:#f14c4c;">(+{(ratio-1)*100:.0f}%)</span>'
        elif ratio < 0.7:
            time_change = f' <span style="color:#4ec9b0;">(-{(1-ratio)*100:.0f}%)</span>'
    
    return f'''
    <div class="outcome-summary">
      <div class="cat-header">Outcome</div>
      <div style="padding: 8px;">
        <div class="outcome-row">
          <span class="outcome-label">Expected:</span>
          <span class="outcome-value">{expected or '-'}</span>
        </div>
        <div class="outcome-row">
          <span class="outcome-label">Actual:</span>
          <span class="outcome-value outcome-{exp_actual_match}">{actual or '?'}</span>
        </div>
        <div class="outcome-row">
          <span class="outcome-label">Result:</span>
          <span class="outcome-value {'outcome-match' if pass_fail == 'PASSED' else 'outcome-mismatch'}">{pass_fail}</span>
        </div>
        <hr style="border-color:#444; margin: 5px 0;">
        <div class="outcome-row">
          <span class="outcome-label">Old time:</span>
          <span class="outcome-value">{old_time_str}</span>
        </div>
        <div class="outcome-row">
          <span class="outcome-label">New time:</span>
          <span class="outcome-value">{new_time_str}{time_change}</span>
        </div>
      </div>
    </div>'''

def generate_test_html(name, status, data):
    """Generate HTML for one test."""
    css_class = "test pass" if status == "PASSED" else "test fail"
    status_class = "pass" if status == "PASSED" else "fail"
    
    html = f'''
<div class="{css_class}">
  <div class="test-header">
    <span class="test-name">{html_escape(name)}</span>
    <span class="status {status_class}">{status}</span>
  </div>
  <div class="categories">'''
    
    # K Source category
    html += generate_category_html(
        "kSource", "K Source",
        data.get('k_source_old'),
        data.get('k_source_new'),
        data.get('k_source_diff')
    )
    
    # K Model Output category
    html += generate_category_html(
        "kModel", "K Model Output",
        data.get('k_model_old'),
        data.get('k_model_new'),
        data.get('k_model_diff')
    )
    
    # SMT Input category
    html += generate_category_html(
        "smtInput", "SMT Input",
        data.get('smt_input_old'),
        data.get('smt_input_new'),
        data.get('smt_input_diff')
    )
    
    # SMT Model Output category
    html += generate_category_html(
        "smtModel", "SMT Model Output",
        data.get('smt_model_old'),
        data.get('smt_model_new'),
        data.get('smt_model_diff')
    )
    
    # Whole Output category
    html += generate_category_html(
        "wholeOutput", "Full Output",
        data.get('output_old'),
        data.get('output_new'),
        data.get('output_diff')
    )
    
    # Outcome Summary
    html += generate_outcome_html(
        data.get('expected'),
        data.get('actual'),
        data.get('old_time'),
        data.get('new_time'),
        status
    )
    
    html += '''
  </div>
</div>'''
    
    return html

def generate_html_footer():
    """Generate HTML footer."""
    return '''
</div>
</body>
</html>
'''

# ============================================================================
# Main Report Generation
# ============================================================================

def generate_report(test_dir_type, output_file=".tmp/comprehensive-report.html", 
                    batch_only=False, run_only=False, timeout=60):
    """Generate the comprehensive HTML report."""
    
    test_dirs = get_test_dirs(test_dir_type)
    
    # Collect test files
    test_files = []
    for dir_path in test_dirs:
        if os.path.exists(dir_path):
            for f in sorted(os.listdir(dir_path)):
                if f.endswith('.k'):
                    test_files.append(os.path.join(dir_path, f))
    
    total = len(test_files)
    print(f"Found {total} test files in {test_dirs}")
    
    # Run tests and cache results (unless batch-only)
    run_results = {}
    if not batch_only:
        print(f"\n=== Running tests and caching outputs ===")
        run_results = run_and_cache_tests(test_files, timeout=timeout)
    
    if run_only:
        print(f"\nRun-only mode: skipping report generation")
        return None
    
    # Load batch results for pass/fail status
    print(f"\n=== Generating report ===")
    batch_results = parse_batch_results()
    
    # Count stats (re-evaluate based on current data)
    passed = 0
    failed = 0
    
    # Start building HTML
    html_parts = []
    test_html_parts = []
    
    # Process each test file
    for i, kfile in enumerate(test_files):
        name = os.path.basename(kfile)
        print(f"Processing {i+1}/{total}: {name}          ", end='\r')
        
        # Get expected outcome from file
        expected = get_expected(kfile)
        
        # Initialize variables
        new_time = None
        actual = None
        old_time = None
        
        # ========== Load OLD (baseline) data ==========
        baseline_json = load_baseline_json(kfile)
        
        # K source: old = baseline.k, new = current file
        k_source_old = load_baseline_file(kfile, '.baseline.k')
        k_source_new = get_current_file(kfile)
        
        # K model output: old = baseline .model.k
        k_model_old = load_baseline_file(kfile, '.model.k')
        
        # SMT input: old = baseline .smt
        smt_input_old = load_baseline_file(kfile, '.smt')
        if not smt_input_old and baseline_json:
            smt_input_old = baseline_json.get('smt', '')
        
        # SMT model output: old = baseline .model.smt
        smt_model_old = load_baseline_file(kfile, '.model.smt')
        if not smt_model_old and baseline_json:
            smt_model_old = baseline_json.get('smtModel', '')
        
        # Full output: old = from baseline JSON
        output_old = baseline_json.get('output', '') if baseline_json else ''
        
        # ========== Load NEW (current run) data ==========
        # First try from run_results (if we just ran), else from cached output/
        if name in run_results:
            run_data = run_results[name]
            new_time = run_data.get('elapsed')
            actual = run_data.get('actual')
            k_model_new = run_data.get('model_k')
            smt_input_new = run_data.get('smt_input')
            smt_model_new = run_data.get('smt_model')
            output_new = run_data.get('output')
        else:
            # Load from cached output/ directory
            k_model_new = load_output_file(kfile, '.model.k')
            smt_input_new = load_output_file(kfile, '.smt')
            smt_model_new = load_output_file(kfile, '.model.smt')
            output_new = load_output_file(kfile, '.output.txt')
            
            # Load metadata for timing and actual result
            meta_json = load_output_file(kfile, '.meta.json')
            if meta_json:
                try:
                    meta = json.loads(meta_json)
                    new_time = meta.get('elapsed')
                    actual = meta.get('actual')
                except:
                    new_time = None
                    actual = None
            else:
                new_time = None
                actual = None
        
        # Determine PASS/FAIL status based on expected vs actual
        if expected and actual:
            # Compare expected vs actual (case-insensitive)
            if expected.upper() == actual.upper():
                status = 'PASSED'
                passed += 1
            else:
                status = 'FAILED'
                failed += 1
        elif actual in ['ERROR', 'TIMEOUT']:
            status = 'FAILED'
            failed += 1
        elif actual:
            # Has actual result but no expectation - treat as passed
            status = 'PASSED'
            passed += 1
        else:
            status = 'UNKNOWN'
        
        # Compute diffs
        k_source_diff = compute_diff(k_source_old, k_source_new, "baseline", "current")
        k_model_diff = compute_diff(k_model_old, k_model_new, "baseline", "current")
        smt_input_diff = compute_diff(smt_input_old, smt_input_new, "baseline", "current")
        smt_model_diff = compute_diff(smt_model_old, smt_model_new, "baseline", "current")
        output_diff = compute_diff(output_old, output_new, "baseline", "current")
        
        # Build data dict
        data = {
            'expected': expected,
            'actual': actual,
            'old_time': old_time,
            'new_time': new_time,
            'k_source_old': k_source_old,
            'k_source_new': k_source_new,
            'k_source_diff': k_source_diff,
            'k_model_old': k_model_old,
            'k_model_new': k_model_new,
            'k_model_diff': k_model_diff,
            'smt_input_old': smt_input_old,
            'smt_input_new': smt_input_new,
            'smt_input_diff': smt_input_diff,
            'smt_model_old': smt_model_old,
            'smt_model_new': smt_model_new,
            'smt_model_diff': smt_model_diff,
            'output_old': output_old,
            'output_new': output_new,
            'output_diff': output_diff,
        }
        
        test_html_parts.append(generate_test_html(name, status, data))
    
    # Build final HTML
    html_parts.append(generate_html_header(total, passed, failed))
    html_parts.extend(test_html_parts)
    html_parts.append(generate_html_footer())
    
    # Write report
    os.makedirs(os.path.dirname(output_file) or '.', exist_ok=True)
    with open(output_file, 'w') as f:
        f.write('\n'.join(html_parts))
    
    print(f"\n\nReport generated: {output_file}")
    print(f"Total: {total}, Passed: {passed}, Failed: {failed}")
    print(f"Open with: open {output_file}")
    return output_file

# ============================================================================
# Entry Point
# ============================================================================

if __name__ == "__main__":
    test_dir = sys.argv[1] if len(sys.argv) > 1 else "tests"
    batch_only = "--batch-only" in sys.argv
    run_only = "--run-only" in sys.argv
    
    # Default timeout
    timeout = 60
    for arg in sys.argv:
        if arg.startswith("--timeout="):
            try:
                timeout = int(arg.split("=")[1])
            except:
                pass
    
    generate_report(test_dir, batch_only=batch_only, run_only=run_only, timeout=timeout)
