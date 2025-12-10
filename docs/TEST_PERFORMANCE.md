# Test Performance Analysis

This document captures performance characteristics of the K language test infrastructure.

## Test Runner Modes

| Mode | Command | Time (119 tests) | Speedup |
|------|---------|------------------|---------|
| Sequential | `./run-tests.sh` | ~145s | 1x |
| Parallel (4 jobs) | `./run-tests.sh -j 4` | ~143s | ~1x |
| **Batch (single JVM)** | `./run-tests.sh -batch` | **~20s** | **6-7x** |

The batch mode is fastest because it avoids the ~0.95s JVM startup overhead per test.

## Per-Test Processing Breakdown

Measured on simple SAT tests (December 2025):

### First Test (includes JVM warmup): ~520ms
| Phase | Time (ms) | % |
|-------|-----------|---|
| State Reset (Z3 context) | 7 | 1% |
| File Parsing (ANTLR) | 307 | 59% |
| Model Combine | 9 | 2% |
| Type Checking | 68 | 13% |
| SMT Generation | 60 | 12% |
| Z3 Solving | 35 | 7% |
| Other overhead | ~34 | 6% |

### Subsequent Tests (warmed up): ~60-200ms
| Phase | Time (ms) | % |
|-------|-----------|---|
| State Reset | 4 | 2-7% |
| File Parsing (ANTLR) | 25-114 | 42-63% |
| Model Combine | 0-4 | 0-2% |
| Type Checking | 2-66 | 3-37% |
| SMT Generation | 6-84 | 10-47% |
| Z3 Solving | 18-25 | 10-30% |

### Key Findings

1. **File Parsing (ANTLR) is the dominant cost** for simple tests - 42-63% of time
2. **Z3 Solving is fast** for simple SAT problems - only 18-35ms
3. **First test has JIT warmup** - parsing takes 307ms vs 25-114ms after warmup
4. **State Reset is cheap** - only 4-7ms per test
5. **Type Checking + SMT Generation** together are ~20-25% of time

## Test Time Categories

| Category | Time Range | Description | Examples |
|----------|-----------|-------------|----------|
| Fast | 0.01-0.05s | Early exceptions (TypeCheck, K2Z3) | `type_inference_error1.k` |
| Medium | 0.05-0.25s | Simple constraints, quick SAT | `inheritance1.k`, `opt1.k` |
| Slow | 0.5-0.8s | Complex solving, UNSAT proofs | `unsat1-5.k`, `timeout_hard*.k` |

## Sequential vs Batch Time Breakdown

### Sequential Mode (~145s for 119 tests)
```
JVM startup (119 × ~0.95s):  ~113s  (78%)
Actual test execution:        ~30s  (21%)
Other overhead:                ~2s  (1%)
```

### Batch Mode (~20s for 119 tests)
```
JVM startup (1×):              ~1s  (5%)
First test warmup:            ~0.5s (2.5%)
119 tests @ ~0.15s avg:       ~18s  (90%)
State resets:                 ~0.5s (2.5%)
```

## Enabling Timing Output

Use the `-timing` flag with batch mode to see detailed timing breakdown:

```bash
./run-tests.sh -batch -timing
```

Example output:
```
[tests] global1.k ... ✅ PASSED (reset=7,parse=307,combine=9,tc=68,smt=60,solve=35) [0.52s]
```

Fields (all times in milliseconds):
- `reset`: State reset time (TypeChecker, UtilSMT, K2Z3)
- `parse`: ANTLR parsing time
- `combine`: Model combination time
- `tc`: Type checking time
- `smt`: SMT generation time
- `solve`: Z3 solving time

## Optimization Opportunities

Based on the analysis:

1. **ANTLR Parsing** (42-63%): Could potentially be optimized by:
   - Caching parsed models
   - Using faster parser generation settings
   - Lazy parsing of imports

2. **JIT Warmup**: First test is ~3x slower - could pre-warm with a dummy model

3. **Z3 Context Creation**: Currently creates new context per test (~4ms) - 
   could potentially reuse with `solver.reset()` instead

## Historical Notes

- **December 2025**: Added batch mode, achieving 6-7x speedup
- Batch mode runs tests sequentially in single JVM to avoid Z3 native library conflicts
- The `optimize` solver must be lazily initialized due to Z3 version mismatch issues

