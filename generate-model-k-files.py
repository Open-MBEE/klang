#!/usr/bin/env python3
"""
One-time generation of .model.k files from existing baseline JSON files.

This reads the 'smtModel' field from each baseline .json file and converts it
to K constraints, saving as .model.k files.

Usage: python3 generate-model-k-files.py [examples|tests|all]
"""

import json
import os
import sys
import re
from pathlib import Path

def parse_smt_model_to_k(smt_model):
    """Convert Z3 SMT model output to K constraints."""
    if not smt_model or smt_model.strip() in ['()', '()\\n', '']:
        return "-- UNSAT: No model available\n"
    
    lines = []
    lines.append("-- K constraints derived from solver model")
    lines.append("-- Generated from smtModel field in baseline JSON")
    lines.append("")
    
    constraints = []
    
    # Parse define-fun statements for constants
    # Format: (define-fun name () Type value)
    define_fun_pattern = r'\(define-fun\s+(\S+)\s+\(\)\s+(\S+)\s+'
    
    # Find all define-fun for constants (no arguments)
    for match in re.finditer(define_fun_pattern, smt_model):
        name = match.group(1)
        type_name = match.group(2)
        
        # Skip internal names
        if is_internal_name(name):
            continue
        
        # Extract the value (everything after type until matching close paren)
        start = match.end()
        value = extract_value(smt_model, start)
        
        if value:
            k_value = format_smt_value_as_k(value, type_name)
            if k_value:
                constraints.append(f"req {name} = {k_value}")
    
    # Parse mk-ClassName expressions to extract object field values
    mk_pattern = r'\(mk-(\w+)\s+([^)]+)\)'
    class_instances = {}
    
    for match in re.finditer(mk_pattern, smt_model):
        class_name = match.group(1)
        field_values = match.group(2).strip().split()
        
        if class_name not in class_instances:
            class_instances[class_name] = []
        class_instances[class_name].append(field_values)
    
    # Add comments for class instances
    for class_name, instances in class_instances.items():
        for i, fields in enumerate(instances):
            if fields:
                constraints.append(f"-- {class_name} instance: {', '.join(fields)}")
    
    # Parse function interpretations
    # Look for (define-fun name ((x!0 Type) (x!1 Type) ...) RetType body)
    func_pattern = r'\(define-fun\s+(\S+)\s+\(\(x!\d+\s+\w+\)'
    for match in re.finditer(func_pattern, smt_model):
        func_name = match.group(1)
        if not is_internal_name(func_name) and '.' in func_name:
            # This is likely a user function like RocketMachine.move
            constraints.append(f"-- Function: {func_name}")
    
    if constraints:
        lines.append(f"-- {len(constraints)} items extracted")
        lines.append("")
        lines.extend(constraints)
    else:
        lines.append("-- No simple constraints could be extracted")
        lines.append("-- The model may contain only complex expressions")
    
    return '\n'.join(lines) + '\n'

def is_internal_name(name):
    """Check if a name is internal/synthetic."""
    return (name.startswith('k!') or
            name.startswith('_xk') or
            name.startswith('const-') or
            '!val!' in name or
            name in ['heap', 'NULL$', 'NULL$Ref'] or
            name.startswith('lift-') or
            name.startswith('mk-') or
            name.startswith('deref'))

def extract_value(s, start):
    """Extract a balanced S-expression value starting at position."""
    if start >= len(s):
        return None
    
    # Skip whitespace
    while start < len(s) and s[start].isspace():
        start += 1
    
    if start >= len(s):
        return None
    
    if s[start] == '(':
        # Nested expression - find matching close
        depth = 1
        end = start + 1
        while end < len(s) and depth > 0:
            if s[end] == '(':
                depth += 1
            elif s[end] == ')':
                depth -= 1
            end += 1
        return s[start:end]
    else:
        # Simple value - read until whitespace or close paren
        end = start
        while end < len(s) and not s[end].isspace() and s[end] != ')':
            end += 1
        return s[start:end]

def format_smt_value_as_k(value, type_name):
    """Format an SMT value as K syntax."""
    value = value.strip()
    
    # Handle negative numbers: (- n)
    if value.startswith('(- ') and value.endswith(')'):
        num = value[3:-1].strip()
        if num.isdigit():
            return f"-{num}"
    
    # Handle simple integers
    if value.lstrip('-').isdigit():
        return value
    
    # Handle booleans
    if value in ['true', 'false']:
        return value
    
    # Handle strings
    if value.startswith('"'):
        return value
    
    # Handle hex bitvectors
    if value.startswith('#x'):
        return value
    
    # Skip complex values
    return None

def process_baseline_file(baseline_file):
    """Process a single baseline JSON file and generate .model.k"""
    try:
        with open(baseline_file, 'r') as f:
            baseline = json.load(f)
    except Exception as e:
        print(f"  Error reading {baseline_file}: {e}")
        return False
    
    smt_model = baseline.get('smtModel', '')
    if not smt_model:
        print(f"  No smtModel in {baseline_file}")
        return False
    
    k_constraints = parse_smt_model_to_k(smt_model)
    
    # Determine output file name
    # baseline/foo.k.json -> baseline/foo.model.k
    base_name = os.path.basename(baseline_file)
    if base_name.endswith('.k.json'):
        model_k_name = base_name[:-7] + '.model.k'  # Remove .k.json, add .model.k
    else:
        model_k_name = base_name.replace('.json', '.model.k')
    
    model_k_path = os.path.join(os.path.dirname(baseline_file), model_k_name)
    
    with open(model_k_path, 'w') as f:
        f.write(k_constraints)
    
    return True

def process_directory(dir_path):
    """Process all baseline files in a directory."""
    baseline_dir = os.path.join(dir_path, 'baseline')
    if not os.path.exists(baseline_dir):
        print(f"No baseline directory in {dir_path}")
        return 0
    
    count = 0
    for f in sorted(os.listdir(baseline_dir)):
        if f.endswith('.k.json'):
            baseline_file = os.path.join(baseline_dir, f)
            print(f"Processing {f}...")
            if process_baseline_file(baseline_file):
                count += 1
    
    return count

def main():
    test_dir = sys.argv[1] if len(sys.argv) > 1 else "all"
    
    dirs = []
    if test_dir in ["examples", "all"]:
        dirs.append("src/examples")
    if test_dir in ["tests", "all"]:
        dirs.append("src/tests")
        dirs.append("src/test")
    
    total = 0
    for d in dirs:
        if os.path.exists(d):
            print(f"\n=== Processing {d} ===")
            count = process_directory(d)
            print(f"Generated {count} .model.k files in {d}/baseline/")
            total += count
    
    print(f"\n✅ Total: {total} .model.k files generated")

if __name__ == "__main__":
    main()
