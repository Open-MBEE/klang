#!/usr/bin/env python3
"""
Baseline Diff Utility

Compares baseline JSON files and shows readable diffs of SMT and other fields.
The SMT is stored as a single-line JSON string, which makes git diffs unreadable.
This tool extracts and formats the fields for easy comparison.

Usage:
    ./diff-baseline.py <baseline.k.json>                # Show current vs git HEAD
    ./diff-baseline.py <baseline.k.json> <commit>       # Show current vs specific commit
    ./diff-baseline.py --field smt <baseline.k.json>    # Show only SMT field
    ./diff-baseline.py --list-changed                   # List baselines that differ from HEAD
"""

import json
import subprocess
import sys
import os
import argparse
import tempfile
from pathlib import Path

def get_git_version(filepath, commit="HEAD"):
    """Get file contents from git at specified commit."""
    try:
        result = subprocess.run(
            ["git", "show", f"{commit}:{filepath}"],
            capture_output=True, text=True, cwd=os.path.dirname(filepath) or "."
        )
        if result.returncode == 0:
            return result.stdout
        return None
    except Exception:
        return None

def load_baseline(content):
    """Parse baseline JSON."""
    try:
        return json.loads(content)
    except json.JSONDecodeError:
        return None

def format_smt(smt_string):
    """Format SMT string for readable diff (unescape and split lines)."""
    if not smt_string:
        return ""
    # The SMT is stored with escaped newlines
    return smt_string.replace("\\n", "\n").replace("\\t", "\t")

def format_json_field(json_obj):
    """Pretty-print JSON field."""
    if not json_obj:
        return ""
    if isinstance(json_obj, str):
        try:
            parsed = json.loads(json_obj)
            return json.dumps(parsed, indent=2)
        except:
            return json_obj
    return json.dumps(json_obj, indent=2)

def diff_strings(old, new, label):
    """Show diff between two strings."""
    if old == new:
        print(f"\n=== {label}: NO CHANGES ===")
        return False
    
    print(f"\n=== {label}: CHANGED ===")
    
    # Use diff command for nice output
    with tempfile.NamedTemporaryFile(mode='w', suffix='.old', delete=False) as f1:
        f1.write(old or "")
        old_file = f1.name
    with tempfile.NamedTemporaryFile(mode='w', suffix='.new', delete=False) as f2:
        f2.write(new or "")
        new_file = f2.name
    
    try:
        result = subprocess.run(
            ["diff", "-u", "--color=always", old_file, new_file],
            capture_output=True, text=True
        )
        if result.stdout:
            # Skip the temp file headers
            lines = result.stdout.split('\n')
            for line in lines[2:]:  # Skip first two header lines
                print(line)
        return True
    finally:
        os.unlink(old_file)
        os.unlink(new_file)

def compare_baselines(old_data, new_data, fields=None):
    """Compare two baseline dictionaries."""
    if fields is None:
        fields = ['outcome', 'typeChecks', 'smt', 'smtModel', 'model']
    
    any_changes = False
    
    for field in fields:
        old_val = old_data.get(field, "") if old_data else ""
        new_val = new_data.get(field, "") if new_data else ""
        
        if field == 'smt':
            old_formatted = format_smt(old_val)
            new_formatted = format_smt(new_val)
        elif field in ['json1', 'json2']:
            old_formatted = format_json_field(old_val)
            new_formatted = format_json_field(new_val)
        else:
            old_formatted = str(old_val) if old_val else ""
            new_formatted = str(new_val) if new_val else ""
        
        if diff_strings(old_formatted, new_formatted, field):
            any_changes = True
    
    return any_changes

def list_changed_baselines():
    """List all baseline files that differ from HEAD."""
    baseline_dirs = ["src/tests/baseline", "src/examples/baseline"]
    
    for bdir in baseline_dirs:
        if not os.path.exists(bdir):
            continue
        
        for f in sorted(os.listdir(bdir)):
            if not f.endswith('.json'):
                continue
            
            filepath = os.path.join(bdir, f)
            git_content = get_git_version(filepath)
            
            if git_content is None:
                print(f"NEW: {filepath}")
                continue
            
            try:
                with open(filepath) as fh:
                    current_content = fh.read()
                
                if current_content != git_content:
                    old_data = load_baseline(git_content)
                    new_data = load_baseline(current_content)
                    
                    changes = []
                    for field in ['outcome', 'smt', 'smtModel', 'json1', 'json2']:
                        old_val = old_data.get(field, "") if old_data else ""
                        new_val = new_data.get(field, "") if new_data else ""
                        if old_val != new_val:
                            changes.append(field)
                    
                    print(f"CHANGED: {filepath} [{', '.join(changes)}]")
            except Exception as e:
                print(f"ERROR: {filepath}: {e}")

def extract_smt(filepath):
    """Extract and print formatted SMT from baseline file."""
    with open(filepath) as f:
        data = json.load(f)
    
    smt = data.get('smt', '')
    print(format_smt(smt))

def main():
    parser = argparse.ArgumentParser(
        description="Compare baseline JSON files with readable diffs"
    )
    parser.add_argument('file', nargs='?', help='Baseline JSON file to compare')
    parser.add_argument('commit', nargs='?', default='HEAD', 
                        help='Git commit to compare against (default: HEAD)')
    parser.add_argument('--field', '-f', action='append',
                        help='Specific field(s) to compare (can use multiple times)')
    parser.add_argument('--list-changed', '-l', action='store_true',
                        help='List all changed baseline files')
    parser.add_argument('--extract-smt', '-s', action='store_true',
                        help='Just extract and print formatted SMT')
    parser.add_argument('--save-smt', metavar='OUTPUT',
                        help='Save formatted SMT to file')
    
    args = parser.parse_args()
    
    if args.list_changed:
        list_changed_baselines()
        return
    
    if not args.file:
        parser.print_help()
        return
    
    filepath = args.file
    
    if args.extract_smt:
        extract_smt(filepath)
        return
    
    if args.save_smt:
        with open(filepath) as f:
            data = json.load(f)
        smt = format_smt(data.get('smt', ''))
        with open(args.save_smt, 'w') as f:
            f.write(smt)
        print(f"Saved SMT to {args.save_smt}")
        return
    
    # Load current file
    with open(filepath) as f:
        current_content = f.read()
    current_data = load_baseline(current_content)
    
    # Get relative path for git
    try:
        rel_path = subprocess.run(
            ["git", "ls-files", "--full-name", filepath],
            capture_output=True, text=True
        ).stdout.strip()
        if not rel_path:
            rel_path = filepath
    except:
        rel_path = filepath
    
    # Load git version
    git_content = get_git_version(rel_path, args.commit)
    if git_content is None:
        print(f"File not found in git at {args.commit}")
        print("This appears to be a new baseline file.")
        return
    
    git_data = load_baseline(git_content)
    
    print(f"Comparing: {filepath}")
    print(f"Against: {args.commit}")
    print("=" * 60)
    
    fields = args.field if args.field else None
    if not compare_baselines(git_data, current_data, fields):
        print("\nNo differences found.")

if __name__ == "__main__":
    main()
