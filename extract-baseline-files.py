#!/usr/bin/env python3
"""
One-time extraction of fields from baseline JSON files to separate files.

Extracts:
- smt → .smt file
- smtModel → .model.smt file
- model (K source) → .baseline.k file  (already done by BaselineFileWriter, but extracting for completeness)

Usage: python3 extract-baseline-files.py [examples|tests|all]
"""

import json
import os
import sys
from pathlib import Path

def extract_files_from_baseline(json_path: Path) -> dict:
    """Extract smt and smtModel from a baseline JSON file."""
    stats = {'smt': False, 'model_smt': False, 'baseline_k': False}
    
    try:
        with open(json_path, 'r') as f:
            data = json.load(f)
    except (json.JSONDecodeError, IOError) as e:
        print(f"  Warning: Could not read {json_path}: {e}")
        return stats
    
    base_path = str(json_path)[:-5]  # Remove .json
    
    # Extract 'smt' field to .smt file
    if 'smt' in data and data['smt']:
        smt_content = data['smt']
        if smt_content.strip():
            smt_path = base_path + '.smt'
            with open(smt_path, 'w') as f:
                f.write(smt_content)
            stats['smt'] = True
    
    # Extract 'smtModel' field to .model.smt file
    if 'smtModel' in data and data['smtModel']:
        model_content = data['smtModel']
        if model_content.strip() and model_content.strip() not in ['()', '()\\n']:
            model_smt_path = base_path + '.model.smt'
            with open(model_smt_path, 'w') as f:
                f.write(model_content)
            stats['model_smt'] = True
    
    # Extract 'model' field (K source) to .baseline.k file
    if 'model' in data and data['model']:
        k_content = data['model']
        if k_content.strip():
            baseline_k_path = base_path + '.baseline.k'
            with open(baseline_k_path, 'w') as f:
                f.write(k_content)
            stats['baseline_k'] = True
    
    return stats

def process_directory(baseline_dir: Path, name: str) -> dict:
    """Process all baseline JSON files in a directory."""
    totals = {'smt': 0, 'model_smt': 0, 'baseline_k': 0, 'total': 0}
    
    if not baseline_dir.exists():
        print(f"Directory not found: {baseline_dir}")
        return totals
    
    json_files = list(baseline_dir.glob("*.json"))
    print(f"\nProcessing {len(json_files)} baseline files in {name}...")
    
    for json_path in sorted(json_files):
        stats = extract_files_from_baseline(json_path)
        totals['total'] += 1
        if stats['smt']:
            totals['smt'] += 1
        if stats['model_smt']:
            totals['model_smt'] += 1
        if stats['baseline_k']:
            totals['baseline_k'] += 1
    
    return totals

def main():
    target = sys.argv[1] if len(sys.argv) > 1 else 'all'
    
    script_dir = Path(__file__).parent
    examples_baseline = script_dir / 'src' / 'examples' / 'baseline'
    tests_baseline = script_dir / 'src' / 'tests' / 'baseline'
    
    grand_totals = {'smt': 0, 'model_smt': 0, 'baseline_k': 0, 'total': 0}
    
    if target in ['examples', 'all']:
        totals = process_directory(examples_baseline, 'examples')
        for k, v in totals.items():
            grand_totals[k] += v
    
    if target in ['tests', 'all']:
        totals = process_directory(tests_baseline, 'tests')
        for k, v in totals.items():
            grand_totals[k] += v
    
    print(f"\n{'='*60}")
    print(f"Extraction complete!")
    print(f"  Total baseline files processed: {grand_totals['total']}")
    print(f"  .smt files created: {grand_totals['smt']}")
    print(f"  .model.smt files created: {grand_totals['model_smt']}")
    print(f"  .baseline.k files created: {grand_totals['baseline_k']}")
    print(f"{'='*60}")

if __name__ == '__main__':
    main()
