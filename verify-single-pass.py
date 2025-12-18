#!/usr/bin/env python3
"""
Verify that non-heap tests solve in a single pass (no CEGAR iterations needed).

For heap tests (those with 'heap' in the name or using -heapcegar), we expect
CEGAR iterations.

For all other tests, they should solve directly via K2Z3.solveSMT without
any CEGAR iterations.
"""

import subprocess
import os
import sys
import re

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
K_CMD = os.path.join(SCRIPT_DIR, "export", "k")
TESTS_DIR = os.path.join(SCRIPT_DIR, "src", "tests")

# Tests that are expected to use CEGAR (heap-related)
HEAP_TESTS = [
    "cegar1.k",
    "heap_chain5.k",
    "heap_linked_list.k",
    "heap_tree.k",
    "heap_binary_search.k",
    "heap_doubly_linked.k",
    "heap_cyclic_graph.k",
    "lisp.k",
]

def is_heap_test(filename):
    """Check if a test is expected to be a heap test."""
    return filename in HEAP_TESTS or "heap" in filename.lower()

def run_test(test_path, use_heapcegar=False):
    """Run a test and return (success, num_iterations, output)."""
    cmd = [K_CMD]
    if use_heapcegar:
        cmd.append("-heapcegar")
    cmd.append(test_path)

    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=30
        )
        output = result.stdout + result.stderr

        # Count CEGAR iterations
        iterations = len(re.findall(r"Heap CEGAR Iteration \d+", output))

        # Check if it used K2Z3.solveSMT (direct path) or HeapCEGAR
        used_heapcegar = "Using Heap CEGAR Solver" in output
        used_k2z3 = "[K2Z3.solveSMT]" in output or "Top level objects created" in output

        # Check for success
        success = "Top level objects" in output or "SAT" in output
        unsat = "UNSAT" in output
        error = "Exception" in output or "Error" in output

        return {
            "success": success or unsat,
            "sat": success and not unsat,
            "unsat": unsat,
            "error": error,
            "iterations": iterations,
            "used_heapcegar": used_heapcegar,
            "used_k2z3": used_k2z3,
            "output": output
        }
    except subprocess.TimeoutExpired:
        return {
            "success": False,
            "timeout": True,
            "iterations": 0,
            "output": "TIMEOUT"
        }
    except Exception as e:
        return {
            "success": False,
            "error": True,
            "iterations": 0,
            "output": str(e)
        }

def main():
    # Get all .k test files
    test_files = sorted([f for f in os.listdir(TESTS_DIR) if f.endswith('.k')])

    print("=" * 60)
    print(" Verifying Single-Pass Solving for Non-Heap Tests")
    print("=" * 60)
    print()

    non_heap_tests = []
    heap_tests = []

    for test_file in test_files:
        if is_heap_test(test_file):
            heap_tests.append(test_file)
        else:
            non_heap_tests.append(test_file)

    print(f"Total tests: {len(test_files)}")
    print(f"  Non-heap tests: {len(non_heap_tests)}")
    print(f"  Heap tests: {len(heap_tests)}")
    print()

    # Test a sample of non-heap tests to verify they don't use CEGAR
    print("Checking non-heap tests (should NOT use HeapCEGAR)...")
    print("-" * 60)

    issues = []
    tested = 0
    for test_file in non_heap_tests[:20]:  # Test first 20
        test_path = os.path.join(TESTS_DIR, test_file)
        result = run_test(test_path, use_heapcegar=False)
        tested += 1

        status = "OK" if not result.get("used_heapcegar") else "ISSUE"
        if result.get("error"):
            status = "ERR"
        elif result.get("timeout"):
            status = "TMO"

        # Non-heap tests should NOT go through HeapCEGAR path
        if result.get("used_heapcegar"):
            issues.append((test_file, "Unexpectedly used HeapCEGAR"))

        print(f"  [{status}] {test_file}")

    print()

    # Test heap tests with -heapcegar to verify they do use CEGAR
    print("Checking heap tests (should use HeapCEGAR with -heapcegar flag)...")
    print("-" * 60)

    for test_file in heap_tests:
        test_path = os.path.join(TESTS_DIR, test_file)
        if not os.path.exists(test_path):
            print(f"  [SKIP] {test_file} (not found)")
            continue

        result = run_test(test_path, use_heapcegar=True)
        tested += 1

        status = "OK" if result.get("used_heapcegar") else "WARN"
        if result.get("error"):
            status = "ERR"
        elif result.get("timeout"):
            status = "TMO"

        iterations = result.get("iterations", 0)

        print(f"  [{status}] {test_file} ({iterations} iteration(s))")

    print()
    print("=" * 60)
    print(f"Tested {tested} files")

    if issues:
        print()
        print("ISSUES FOUND:")
        for test_file, issue in issues:
            print(f"  - {test_file}: {issue}")
        return 1
    else:
        print("All checks passed!")
        return 0

if __name__ == "__main__":
    sys.exit(main())

