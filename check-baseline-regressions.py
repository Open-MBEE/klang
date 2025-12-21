#!/usr/bin/env python3
"""
Baseline Regression Analysis Tool

Analyzes git history of baseline.json to find suspicious changes.
Also provides inventory of all K files and their baseline status.

Outcome categories:
  - SAT_MODEL: satisfiable with model
  - SAT_NOMODEL: satisfiable but no model (no instance vars)
  - UNSAT: unsatisfiable
  - TYPECHECK_FAIL: expected type error
  - CRASH: exception or missing output

Algorithm for suspicious changes:
  IGNORE (not suspicious):
    1. Baseline added for a file
    2. Outcome is SAT AND model changed AND file changed in same commit
  
  SUSPICIOUS (should investigate):
    - Everything else: outcome changed, model changed without file change, removed
"""

import json
import subprocess
import sys
import os
from pathlib import Path
from dataclasses import dataclass
from typing import Optional, Dict, List, Tuple
import hashlib


@dataclass
class Outcome:
    """Represents the outcome of running a K file"""
    category: str  # SAT_MODEL, SAT_NOMODEL, UNSAT, TYPECHECK_FAIL, CRASH
    type_checks: bool
    model_hash: Optional[str] = None  # hash of smtModel for comparison
    
    @staticmethod
    def from_baseline_entry(entry: dict) -> 'Outcome':
        """Create Outcome from a baseline.json entry"""
        tc = entry.get('typeChecks', True)
        smt_model = entry.get('smtModel', '')
        smt = entry.get('smt', '')
        
        if not tc:
            return Outcome('TYPECHECK_FAIL', False)
        elif smt_model == '()':
            return Outcome('UNSAT', True)
        elif smt_model == '' or smt_model is None:
            if smt:
                return Outcome('SAT_NOMODEL', True)
            else:
                return Outcome('TYPECHECK_FAIL', False)
        else:
            # Hash the model for comparison
            model_hash = hashlib.md5(smt_model.encode()).hexdigest()[:8]
            return Outcome('SAT_MODEL', True, model_hash)


@dataclass  
class BaselineChange:
    """Represents a change to a baseline entry"""
    filename: str
    commit: str
    commit_date: str
    commit_msg: str
    change_type: str  # ADDED, REMOVED, MODIFIED
    old_outcome: Optional[Outcome]
    new_outcome: Optional[Outcome]
    file_changed_in_commit: bool
    is_suspicious: bool
    reason: str


def run_git(args: List[str], cwd: str = '.') -> str:
    """Run a git command and return output"""
    result = subprocess.run(
        ['git', '--no-pager'] + args,
        capture_output=True, text=True, cwd=cwd
    )
    return result.stdout.strip()


def get_baseline_at_commit(commit: str) -> Optional[dict]:
    """Get baseline.json content at a specific commit"""
    try:
        content = run_git(['show', f'{commit}:src/tests/baseline.json'])
        if content:
            return json.loads(content)
    except (json.JSONDecodeError, subprocess.SubprocessError):
        pass
    return None


def get_files_changed_in_commit(commit: str) -> set:
    """Get list of files changed in a commit"""
    output = run_git(['diff-tree', '--no-commit-id', '--name-only', '-r', commit])
    return set(output.split('\n')) if output else set()


def get_commit_info(commit: str) -> Tuple[str, str]:
    """Get commit date and message"""
    date = run_git(['log', '-1', '--format=%ci', commit])
    msg = run_git(['log', '-1', '--format=%s', commit])
    return date, msg


def analyze_baseline_history() -> List[BaselineChange]:
    """Analyze all commits to baseline.json and find changes"""
    changes = []
    
    # Get all commits that touched baseline.json
    commits_output = run_git(['log', '--oneline', '--follow', '--', 'src/tests/baseline.json'])
    commits = [line.split()[0] for line in commits_output.split('\n') if line]
    
    print(f"Found {len(commits)} commits to baseline.json")
    
    # Process in reverse order (oldest first)
    commits = list(reversed(commits))
    
    prev_baseline = {}
    
    for commit in commits:
        date, msg = get_commit_info(commit)
        curr_baseline = get_baseline_at_commit(commit) or {}
        files_in_commit = get_files_changed_in_commit(commit)
        
        # Find all files that changed between prev and curr
        all_files = set(prev_baseline.keys()) | set(curr_baseline.keys())
        
        for filename in all_files:
            old_entry = prev_baseline.get(filename)
            new_entry = curr_baseline.get(filename)
            
            # Determine change type
            if old_entry is None and new_entry is not None:
                change_type = 'ADDED'
                old_outcome = None
                new_outcome = Outcome.from_baseline_entry(new_entry)
            elif old_entry is not None and new_entry is None:
                change_type = 'REMOVED'
                old_outcome = Outcome.from_baseline_entry(old_entry)
                new_outcome = None
            elif old_entry != new_entry:
                change_type = 'MODIFIED'
                old_outcome = Outcome.from_baseline_entry(old_entry)
                new_outcome = Outcome.from_baseline_entry(new_entry)
            else:
                continue  # No change
            
            # Check if the .k file itself changed in this commit
            k_file_path = f'src/tests/{filename}'
            file_changed = k_file_path in files_in_commit
            
            # Apply the algorithm:
            # IGNORE (not suspicious):
            #   1. Baseline added for a file
            #   2. Outcome is SAT AND model changed AND file changed in same commit
            # SUSPICIOUS: everything else
            
            is_suspicious = False
            reason = ''
            
            if change_type == 'ADDED':
                # Case 1: baseline added - NOT suspicious
                is_suspicious = False
                reason = f'Added ({new_outcome.category})'
                    
            elif change_type == 'REMOVED':
                # Baseline removed - SUSPICIOUS
                is_suspicious = True
                reason = f'REMOVED (was {old_outcome.category})'
                
            elif change_type == 'MODIFIED':
                # Check if outcome category changed
                if old_outcome.category != new_outcome.category:
                    # Outcome category changed - SUSPICIOUS
                    is_suspicious = True
                    reason = f'OUTCOME: {old_outcome.category} -> {new_outcome.category}'
                elif old_outcome.category in ('SAT_MODEL', 'SAT_NOMODEL'):
                    # SAT outcome - check model change
                    if old_outcome.model_hash != new_outcome.model_hash:
                        if file_changed:
                            # Case 2: SAT, model changed, file changed - NOT suspicious
                            is_suspicious = False
                            reason = 'Model changed (file modified)'
                        else:
                            # Model changed but file didn't - SUSPICIOUS
                            is_suspicious = True
                            reason = 'MODEL changed (file NOT modified)'
                    else:
                        # Model same, other fields changed
                        is_suspicious = False
                        reason = 'Other fields changed'
                else:
                    # Non-SAT modified (UNSAT or TYPECHECK_FAIL)
                    is_suspicious = False
                    reason = 'Entry modified (non-SAT)'
            
            changes.append(BaselineChange(
                filename=filename,
                commit=commit,
                commit_date=date,
                commit_msg=msg[:50],
                change_type=change_type,
                old_outcome=old_outcome,
                new_outcome=new_outcome,
                file_changed_in_commit=file_changed,
                is_suspicious=is_suspicious,
                reason=reason
            ))
        
        prev_baseline = curr_baseline
    
    return changes


def run_k_file(filepath: str) -> Outcome:
    """Run a K file and determine its outcome"""
    try:
        result = subprocess.run(
            ['./export/k', filepath],
            capture_output=True, text=True, timeout=30
        )
        output = result.stdout + result.stderr
        
        # Check for SUCCESS patterns FIRST
        if 'Top level objects created' in output or 'Extra objects created' in output:
            return Outcome('SAT_MODEL', True)
        elif 'No instance variables were declared' in output:
            return Outcome('SAT_NOMODEL', True)
        elif 'is unsatisfiable' in output.lower() or 'smtModel=()' in output:
            return Outcome('UNSAT', True)
        # Then check for ERROR patterns
        elif 'TypeCheckException' in output:
            return Outcome('TYPECHECK_FAIL', False)
        elif 'Exception' in output:
            return Outcome('CRASH', False)
        elif 'Type checking completed. No errors found' in output:
            return Outcome('SAT_NOMODEL', True)
        else:
            return Outcome('CRASH', False)
    except subprocess.TimeoutExpired:
        return Outcome('CRASH', False)
    except Exception as e:
        return Outcome('CRASH', False)


def print_changes_table(changes: List[BaselineChange], suspicious_only: bool = False):
    """Print all baseline changes as a table"""
    if suspicious_only:
        changes = [c for c in changes if c.is_suspicious]
    
    if not changes:
        print("No changes found.")
        return
    
    print("\n" + "=" * 140)
    print("BASELINE CHANGES (** = suspicious, investigate)")
    print("=" * 140)
    print(f"{'S':<3} {'File':<22} {'Change':<10} {'Old':<14} {'New':<14} {'Commit':<10} {'Date':<12} {'Reason':<45}")
    print("-" * 140)
    
    for c in changes:
        old_cat = c.old_outcome.category if c.old_outcome else '-'
        new_cat = c.new_outcome.category if c.new_outcome else '-'
        
        flag = '**' if c.is_suspicious else '  '
        print(f"{flag} {c.filename:<22} {c.change_type:<10} {old_cat:<14} {new_cat:<14} {c.commit:<10} {c.commit_date[:10]:<12} {c.reason:<45}")
    
    print("-" * 140)
    suspicious_count = len([c for c in changes if c.is_suspicious])
    print(f"Total: {len(changes)} changes, {suspicious_count} suspicious (marked with **)")


def get_all_k_files_inventory(baseline: dict) -> List[Tuple[str, str, str, str]]:
    """Get inventory of ALL K files in tests and examples"""
    inventory = []
    
    # Check src/tests/
    tests_dir = Path('src/tests')
    for k_file in sorted(tests_dir.glob('*.k')):
        filename = k_file.name
        filepath = str(k_file)
        
        if filename in baseline:
            entry = baseline[filename]
            outcome = Outcome.from_baseline_entry(entry)
            has_baseline = 'YES'
            baseline_outcome = outcome.category
        else:
            has_baseline = 'NO'
            baseline_outcome = '-'
        
        # Get current run outcome
        current_outcome = run_k_file(filepath)
        
        inventory.append((filepath, has_baseline, baseline_outcome, current_outcome.category))
    
    # Check src/examples/
    examples_dir = Path('src/examples')
    for k_file in sorted(examples_dir.glob('*.k')):
        filepath = str(k_file)
        has_baseline = 'NO'  # examples don't have baselines
        baseline_outcome = '-'
        
        current_outcome = run_k_file(filepath)
        inventory.append((filepath, has_baseline, baseline_outcome, current_outcome.category))
    
    return inventory


def print_inventory_table(inventory: List[Tuple[str, str, str, str]]):
    """Print full inventory of K files"""
    print("\n" + "=" * 110)
    print("K FILES INVENTORY (all files in src/tests and src/examples)")
    print("=" * 110)
    print(f"{'File':<45} {'Baseline?':<12} {'Baseline Outcome':<18} {'Current Outcome':<18}")
    print("-" * 110)
    
    for filepath, has_baseline, baseline_outcome, current_outcome in inventory:
        # Flag mismatches
        if has_baseline == 'YES' and baseline_outcome != current_outcome:
            flag = '!!'  # Mismatch between baseline and current
        elif has_baseline == 'NO' and current_outcome not in ('SAT_MODEL', 'SAT_NOMODEL'):
            flag = '? '  # No baseline and not SAT
        else:
            flag = '  '
        print(f"{flag}{filepath:<43} {has_baseline:<12} {baseline_outcome:<18} {current_outcome:<18}")
    
    print("-" * 110)
    
    # Summary
    total = len(inventory)
    with_baseline = len([x for x in inventory if x[1] == 'YES'])
    without_baseline = total - with_baseline
    mismatches = len([x for x in inventory if x[1] == 'YES' and x[2] != x[3]])
    no_baseline_not_sat = len([x for x in inventory if x[1] == 'NO' and x[3] not in ('SAT_MODEL', 'SAT_NOMODEL')])
    
    print(f"Total: {total} files")
    print(f"  With baseline: {with_baseline}")
    print(f"  Without baseline: {without_baseline}")
    print(f"  Baseline/current mismatch (!!): {mismatches}")
    print(f"  No baseline + not SAT (?): {no_baseline_not_sat}")


def print_representative_baselines(baseline: dict):
    """Print representative baseline entries, pretty-printed"""
    print("\n" + "=" * 80)
    print("REPRESENTATIVE BASELINE ENTRIES (pretty-printed)")
    print("=" * 80)
    
    # Find examples of each outcome type
    examples = {
        'SAT_MODEL': None,
        'SAT_NOMODEL': None,
        'UNSAT': None,
        'TYPECHECK_FAIL': None,
    }
    
    for filename, entry in baseline.items():
        outcome = Outcome.from_baseline_entry(entry)
        if examples[outcome.category] is None:
            examples[outcome.category] = (filename, entry)
    
    for category, data in examples.items():
        if data is None:
            print(f"\n--- {category}: (none found) ---")
            continue
            
        filename, entry = data
        print(f"\n--- {category}: {filename} ---")
        
        # Pretty print, but truncate long fields
        display_entry = {}
        for key, value in entry.items():
            if key in ('smt', 'smtModel') and isinstance(value, str) and len(value) > 300:
                # Show first/last part
                display_entry[key] = value[:150] + f'\n... ({len(value)} chars total) ...\n' + value[-100:]
            elif key in ('json1', 'json2'):
                display_entry[key] = f'<{type(value).__name__} with {len(json.dumps(value))} chars>'
            else:
                display_entry[key] = value
        
        print(json.dumps(display_entry, indent=2))


def main():
    import argparse
    parser = argparse.ArgumentParser(description='Analyze baseline.json regression history')
    parser.add_argument('--changes', action='store_true', help='Show table of baseline changes to investigate')
    parser.add_argument('--inventory', action='store_true', help='Show all K files with baseline status')
    parser.add_argument('--examples', action='store_true', help='Show representative baseline entries (pretty-printed)')
    parser.add_argument('--suspicious', action='store_true', help='Show only suspicious changes (with --changes)')
    parser.add_argument('--all', action='store_true', help='Run all reports')
    args = parser.parse_args()
    
    # Default to showing help if no args
    if not any([args.changes, args.inventory, args.examples, args.all]):
        parser.print_help()
        print("\nExamples:")
        print("  python3 check-baseline-regressions.py --changes              # Show baseline change history")
        print("  python3 check-baseline-regressions.py --changes --suspicious # Show only suspicious changes")
        print("  python3 check-baseline-regressions.py --inventory            # Show all K files")
        print("  python3 check-baseline-regressions.py --examples             # Show sample baselines")
        print("  python3 check-baseline-regressions.py --all                  # Run all reports")
        return
    
    os.chdir(Path(__file__).parent)
    
    # Load current baseline
    baseline = json.load(open('src/tests/baseline.json'))
    
    if args.changes or args.all:
        print("Analyzing baseline.json git history...")
        changes = analyze_baseline_history()
        print_changes_table(changes, suspicious_only=args.suspicious)
    
    if args.inventory or args.all:
        print("\nBuilding K files inventory (this may take a minute)...")
        inventory = get_all_k_files_inventory(baseline)
        print_inventory_table(inventory)
    
    if args.examples or args.all:
        print_representative_baselines(baseline)


if __name__ == '__main__':
    main()
