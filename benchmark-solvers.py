#!/usr/bin/env python3
"""
Solver Benchmark Script for K Language
Compares Z3, CVC5, MiniZinc, and BAE performance across test files.
"""

import subprocess
import time
import os
import sys
import csv
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeoutError

# Configuration
TIMEOUT = 30  # seconds per test
K_EXECUTABLE = "./export/k"
OUTPUT_DIR = Path(".tmp")
RESULTS_CSV = OUTPUT_DIR / "solver_benchmark_results.csv"
REPORT_MD = OUTPUT_DIR / "solver_benchmark_report.md"

def check_solver_available(name, check_cmd):
    """Check if a solver is available."""
    try:
        subprocess.run(check_cmd, shell=True, capture_output=True, timeout=5)
        return True
    except:
        return False

def run_k_with_solver(k_file, solver_flag=None, timeout=TIMEOUT):
    """Run a K file with the specified solver and return (time, status)."""
    cmd = [K_EXECUTABLE]
    if solver_flag:
        cmd.append(solver_flag)
    cmd.append(k_file)

    start = time.time()
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
            cwd=os.path.dirname(os.path.abspath(__file__)) or "."
        )
        elapsed = time.time() - start
        output = result.stdout + result.stderr

        if "SAT" in output and "UNSAT" not in output:
            status = "sat"
        elif "UNSAT" in output:
            status = "unsat"
        elif "error" in output.lower() or "exception" in output.lower():
            status = "error"
        else:
            status = "unknown"

        return (f"{elapsed:.3f}", status)
    except subprocess.TimeoutExpired:
        return ("timeout", "timeout")
    except Exception as e:
        return ("error", str(e)[:20])

def find_test_files():
    """Find all K test files."""
    test_dirs = ["src/tests", "src/test", "src/examples"]
    files = []
    for d in test_dirs:
        if os.path.isdir(d):
            for f in Path(d).glob("*.k"):
                files.append(str(f))
    return sorted(files)

def get_file_comment_style(filepath):
    """Determine the predominant comment style in a file."""
    try:
        with open(filepath, 'r') as f:
            content = f.read()

        double_slash = content.count('//')
        double_dash = content.count('--')

        if double_dash > double_slash:
            return "--"
        return "//"
    except:
        return "//"

def add_preferred_solver_comment(filepath, solver):
    """Add @preferred_solver comment to a file, matching existing style."""
    comment_style = get_file_comment_style(filepath)

    with open(filepath, 'r') as f:
        content = f.read()

    # Check if already has preferred_solver
    if "@preferred_solver" in content:
        print(f"  Already has @preferred_solver annotation, skipping")
        return False

    # Add at the beginning
    new_line = f"{comment_style} @preferred_solver {solver}\n"

    with open(filepath, 'w') as f:
        f.write(new_line + content)

    return True

def main():
    # Ensure output directory exists
    OUTPUT_DIR.mkdir(exist_ok=True)

    # Check solver availability
    print("=== Solver Availability ===")
    has_z3 = True  # Always available via K
    has_cvc5 = check_solver_available("CVC5", "which cvc5")
    has_minizinc = check_solver_available("MiniZinc", "which minizinc")
    has_bae = os.path.isdir(os.path.expanduser("~/git/kservices"))

    print(f"  Z3:       ✓ (default)")
    print(f"  CVC5:     {'✓' if has_cvc5 else '✗'}")
    print(f"  MiniZinc: {'✓' if has_minizinc else '✗'}")
    print(f"  BAE:      {'✓' if has_bae else '✗'} (benchmark only, not for annotation)")
    print()

    # Find test files
    test_files = find_test_files()
    print(f"=== Found {len(test_files)} test files ===")
    print()

    # Run benchmarks
    results = []
    recommendations = []

    for i, filepath in enumerate(test_files):
        filename = os.path.basename(filepath)
        print(f"[{i+1:3d}/{len(test_files)}] {filename:40s} ", end="", flush=True)

        # Z3 (default)
        z3_time, z3_status = run_k_with_solver(filepath)

        # CVC5
        if has_cvc5:
            cvc5_time, cvc5_status = run_k_with_solver(filepath, "-cvc5")
        else:
            cvc5_time, cvc5_status = "n/a", "n/a"

        # MiniZinc
        if has_minizinc:
            mzn_time, mzn_status = run_k_with_solver(filepath, "-minizinc")
        else:
            mzn_time, mzn_status = "n/a", "n/a"

        # BAE (placeholder)
        bae_time, bae_status = "n/a", "n/a"

        print(f"Z3:{z3_time:>8s}  CVC5:{cvc5_time:>8s}  MZN:{mzn_time:>8s}")

        row = {
            'file': filepath,
            'z3_time': z3_time, 'z3_status': z3_status,
            'cvc5_time': cvc5_time, 'cvc5_status': cvc5_status,
            'minizinc_time': mzn_time, 'minizinc_status': mzn_status,
            'bae_time': bae_time, 'bae_status': bae_status,
        }
        results.append(row)

        # Determine if non-Z3 solver is significantly better (>2x faster)
        def parse_time(t):
            try:
                return float(t)
            except:
                return 999.0

        z3_t = parse_time(z3_time)
        cvc5_t = parse_time(cvc5_time)
        mzn_t = parse_time(mzn_time)

        # Only recommend if >2x faster AND actually solved it
        recommended = None
        speedup = 1.0
        if cvc5_t < z3_t / 2 and cvc5_t < mzn_t and cvc5_status in ['sat', 'unsat']:
            recommended = 'cvc5'
            speedup = z3_t / cvc5_t
        elif mzn_t < z3_t / 2 and mzn_t < cvc5_t and mzn_status in ['sat', 'unsat']:
            recommended = 'minizinc'
            speedup = z3_t / mzn_t

        if recommended:
            recommendations.append({
                'file': filepath,
                'solver': recommended,
                'z3_time': z3_t,
                'alt_time': cvc5_t if recommended == 'cvc5' else mzn_t,
                'speedup': speedup
            })

    # Write CSV results
    with open(RESULTS_CSV, 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['file', 'z3_time', 'z3_status',
                                                'cvc5_time', 'cvc5_status',
                                                'minizinc_time', 'minizinc_status',
                                                'bae_time', 'bae_status'])
        writer.writeheader()
        writer.writerows(results)

    # Generate markdown report
    with open(REPORT_MD, 'w') as f:
        f.write("# Solver Benchmark Report\n\n")
        f.write(f"**Date:** {time.strftime('%Y-%m-%d %H:%M')}\n\n")
        f.write(f"**Total files tested:** {len(results)}\n\n")

        # Full results table
        f.write("## Full Results\n\n")
        f.write("| File | Z3 | CVC5 | MiniZinc | BAE |\n")
        f.write("|------|----|----- |----------|-----|\n")

        for row in results:
            filename = os.path.basename(row['file'])
            f.write(f"| {filename} | {row['z3_time']}s ({row['z3_status']}) | "
                    f"{row['cvc5_time']}s ({row['cvc5_status']}) | "
                    f"{row['minizinc_time']}s ({row['minizinc_status']}) | "
                    f"{row['bae_time']}s ({row['bae_status']}) |\n")

        # Recommendations
        f.write("\n## Files to Annotate with @preferred_solver\n\n")
        f.write("These files have a solver that is >2x faster than Z3:\n\n")

        if recommendations:
            f.write("| File | Recommended | Z3 Time | Alt Time | Speedup |\n")
            f.write("|------|-------------|---------|----------|--------|\n")
            for rec in sorted(recommendations, key=lambda x: -x['speedup']):
                f.write(f"| {rec['file']} | `{rec['solver']}` | {rec['z3_time']:.3f}s | "
                        f"{rec['alt_time']:.3f}s | **{rec['speedup']:.1f}x** |\n")
        else:
            f.write("No files found where alternative solvers significantly outperform Z3.\n")

    print()
    print(f"Results saved to: {RESULTS_CSV}")
    print(f"Report saved to: {REPORT_MD}")
    print()

    # Ask about annotating files
    if recommendations:
        print(f"=== {len(recommendations)} files could benefit from @preferred_solver ===")
        print()
        for rec in sorted(recommendations, key=lambda x: -x['speedup']):
            print(f"  {rec['file']}: {rec['solver']} ({rec['speedup']:.1f}x faster)")
        print()

        if len(sys.argv) > 1 and sys.argv[1] == "--annotate":
            print("Annotating files...")
            for rec in recommendations:
                filepath = rec['file']
                solver = rec['solver']
                print(f"  {filepath}: adding @preferred_solver {solver}")
                add_preferred_solver_comment(filepath, solver)
            print("Done!")
        else:
            print("Run with --annotate to add @preferred_solver comments to these files")

    return recommendations

if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.abspath(__file__)) or ".")
    main()

