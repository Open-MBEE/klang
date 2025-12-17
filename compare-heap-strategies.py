#!/usr/bin/env python3
"""Compare different heap/solving strategies for K files."""

import subprocess
import sys
import time
import os

# Find K executable
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
K_CMD = os.path.join(SCRIPT_DIR, "export", "k")

# ANSI colors
GREEN = '\033[0;32m'
RED = '\033[0;31m'
YELLOW = '\033[1;33m'
NC = '\033[0m'

# Strategies: name -> flag
STRATEGIES = [
    ("unbounded", ""),
    ("bounded", "-heap"),
    ("cegar", "-heapcegar"),
    ("softbounded", "-heapsoftbounded"),
]

# Default test files
DEFAULT_FILES = [
    "src/tests/cegar1.k",
    "src/tests/heap_chain5.k",
    "src/tests/heap_linked_list.k",
    "src/tests/heap_tree.k",
    "src/tests/heap_binary_search.k",
    "src/tests/heap_doubly_linked.k",
    "src/tests/heap_cyclic_graph.k",
]

def run_test(file_path, flag, timeout=30):
    """Run K with given strategy and return (result, time_ms)."""
    cmd = [K_CMD]
    if flag:
        cmd.append(flag)
    cmd.append(file_path)

    start = time.time()
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout
        )
        elapsed_ms = int((time.time() - start) * 1000)
        output = result.stdout + result.stderr

        if "UNSAT" in output:
            return "UNSAT", elapsed_ms
        elif "Exception" in output or "Error" in output:
            return "ERROR", elapsed_ms
        elif "Top level objects" in output:
            return "SAT", elapsed_ms
        elif result.returncode == 0:
            return "SAT", elapsed_ms
        else:
            return "ERROR", elapsed_ms

    except subprocess.TimeoutExpired:
        elapsed_ms = int((time.time() - start) * 1000)
        return "TIMEOUT", elapsed_ms

def color_result(result, elapsed):
    """Format result with color."""
    if result == "SAT":
        color = GREEN
    elif result == "UNSAT":
        color = RED
    elif result == "TIMEOUT":
        color = YELLOW
    else:
        color = RED
    return f"{color}{result:<7}{NC}{elapsed:>5}ms"

def main():
    files = sys.argv[1:] if len(sys.argv) > 1 else DEFAULT_FILES

    print("=" * 50)
    print(" Heap Strategy Comparison")
    print("=" * 50)
    print()
    print(f"Files: {', '.join(os.path.basename(f) for f in files)}")
    print()

    # Header
    header = f"{'File':<25}"
    for name, _ in STRATEGIES:
        header += f" {name:<12}"
    print(header)
    print("-" * len(header.replace('\033[0m', '').replace('\033[0;32m', '')))

    for file_path in files:
        if not os.path.exists(file_path):
            print(f"Warning: File not found: {file_path}")
            continue

        basename = os.path.basename(file_path)
        line = f"{basename:<25}"

        for name, flag in STRATEGIES:
            result, elapsed = run_test(file_path, flag)
            line += f" {color_result(result, elapsed):<12}"

        print(line)

    print()
    print("Legend: SAT=satisfiable, UNSAT=unsatisfiable, TIMEOUT=30s, ERROR=solver error")

if __name__ == "__main__":
    main()

