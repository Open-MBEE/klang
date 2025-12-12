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

## Parallelization Constraints

### Why Batch Mode Cannot Parallelize Within a Single JVM

The current architecture uses **singleton objects with mutable state**:

- `object TypeChecker` - mutable maps (`exp2Type`, `classes`, `globalTypeEnv`, etc.)
- `object K2Z3` - mutable Z3 `Context`, `Solver`, `z3Model`
- `object UtilSMT` - mutable counters and maps

This state is **reset between tests** but cannot be safely shared across threads.

### Z3 Thread Safety

Z3's Java API has specific threading requirements:
- Each `Context` is thread-safe within itself
- Multiple threads should **NOT** share a single Context for concurrent solving
- The recommended approach is **one Context per thread**

### Parallelization Options

| Option | Effort | Description |
|--------|--------|-------------|
| **Process-level (`-j N`)** | Already done | Run N JVM processes in parallel. Safe but has JVM startup overhead. |
| **Instance-based refactor** | Major (~1000 LOC) | Convert `object` singletons to `class` instances. Each test gets isolated state. |
| **ThreadLocal state** | Medium | Wrap mutable state in `ThreadLocal`. Risk of subtle bugs with shared references. |

### Current Recommendation

**Batch mode (sequential, single JVM)** is the best balance:
- 6-7x faster than sequential multi-JVM
- Avoids Z3 native library conflicts
- No threading complexity

For further speedup, use **process-level parallelism** (`-j 4`) for tests that benefit from it, though gains are limited by JVM startup overhead.

## Historical Notes

- **December 2025**: Added batch mode, achieving 6-7x speedup
- Batch mode runs tests sequentially in single JVM to avoid Z3 native library conflicts
- The `optimize` solver must be lazily initialized due to Z3 version mismatch issues

## Baseline History Analysis

To detect tests that may have been incorrectly changed to expect failures, use git history analysis:

### Finding Tests Changed to Expect Exceptions

```bash
# Find commits where EXCEPTION was added to baseline
git log -p --all -S 'EXCEPTION' -- src/tests/baseline.json

# Find specific tests that changed from SAT/UNSAT to EXCEPTION  
git log -p -- src/tests/baseline.json | grep -B5 -A5 '"EXCEPTION"'

# Show git blame to see when each test expectation was set
git blame src/tests/baseline.json
```

### Current Baseline Structure

The `src/tests/baseline.json` file tracks expected test outcomes:
- `SAT` - Test should find satisfying solution
- `UNSAT` - Test should prove unsatisfiability  
- `EXCEPTION` - Test is expected to throw an exception

### Red Flags to Look For

When reviewing baseline history, look for:
1. Tests that changed from `SAT` → `EXCEPTION` (was working, now broken)
2. Tests that changed from `UNSAT` → `EXCEPTION` (was proving, now broken)
3. Multiple tests changing to EXCEPTION in a single commit (possible regression)

### Investigation Commands

```bash
# Compare current baseline to a known good state
git diff <good-commit> HEAD -- src/tests/baseline.json

# Find all changes to a specific test's expected outcome
git log -p --all -S 'testname.k' -- src/tests/baseline.json

# List commits that modified baseline expectations
git log --oneline -- src/tests/baseline.json
```

### Current Tests Expecting Exceptions (December 2025)

Based on baseline.json, these tests currently expect exceptions:
- `k/StringArray1.k` - EXCEPTION
- `k/redefine_class1.k` - EXCEPTION  
- `k/redefine_function1.k` - EXCEPTION
- `k/type_inference_error1.k` - EXCEPTION
- `k/type_inference_error2.k` - EXCEPTION

For these "expected exception" tests, verify they are intentionally testing error handling rather than being tests that broke and were marked as expected failures.

## Baseline Change Investigation Results (December 2025)

### Investigation Method

To find tests that were changed from passing (SAT/UNSAT) to expecting failures (EXCEPTION):

```bash
# Look at git history for baseline.json
git log --oneline -p -- src/tests/baseline.json

# Search for commits where EXCEPTION was added
git log -p -S 'EXCEPTION' -- src/tests/baseline.json

# Compare baseline between commits
git diff <old-commit> <new-commit> -- src/tests/baseline.json
```

### Findings

Analyzed 4 commits that modified the baseline:

| Commit | Date | Change Summary |
|--------|------|----------------|
| `5d8f80c` | 2025-12-06 | Added `type_inference_error2.k` with EXCEPTION (was UNSAT) |
| `38aef67` | 2025-12-06 | Added `redefine_function1.k` with EXCEPTION |
| `a1ae1f3` | 2025-12-06 | Initial baseline with 118 tests |
| `c3f12f2` | 2025-12-06 | Added `redefine_class1.k` with EXCEPTION |

### Suspicious Changes Identified

**`type_inference_error2.k`**: Changed from expecting `UNSAT` to `EXCEPTION`

```diff
-  "k/type_inference_error2.k": "UNSAT"
+  "k/type_inference_error2.k": "EXCEPTION"
```

This test intentionally assigns an `Int` to a `String` variable:
```k
var a: String = 5
```

The change from UNSAT to EXCEPTION likely reflects an improvement where the **type checker** now catches this error at compile time, rather than letting the constraint through to Z3. This appears to be an **intentional improvement**, not a regression.

### Tests Confirmed as Intentional Error Cases

All current EXCEPTION tests appear to be intentionally testing error detection:

| Test | Purpose | Verdict |
|------|---------|---------|
| `k/StringArray1.k` | Tests unsupported string array operations | ✅ Intentional |
| `k/redefine_class1.k` | Tests class redefinition error | ✅ Intentional |
| `k/redefine_function1.k` | Tests function redefinition error | ✅ Intentional |
| `k/type_inference_error1.k` | Tests type inference error detection | ✅ Intentional |
| `k/type_inference_error2.k` | Tests type error detection (improved from UNSAT) | ✅ Intentional |

### Ongoing Monitoring Script

To detect future suspicious baseline changes, add this to CI:

```bash
#!/bin/bash
# check-baseline-regressions.sh

# Find any tests that changed from SAT/UNSAT to EXCEPTION
git diff HEAD~1 HEAD -- src/tests/baseline.json | grep -E '^\-.*"(SAT|UNSAT)"' | while read line; do
    test_name=$(echo "$line" | grep -o '"[^"]*\.k"' | tr -d '"')
    new_status=$(git diff HEAD~1 HEAD -- src/tests/baseline.json | grep "$test_name" | grep '^+' | grep -o '"[A-Z]*"$' | tr -d '"')
    if [ "$new_status" = "EXCEPTION" ]; then
        echo "⚠️  WARNING: $test_name changed from passing to EXCEPTION - verify this is intentional"
    fi
done
```

## Web Application Examples (src/examples/) - December 2025

### Investigation Summary

The `src/tests/test-webapp-examples.sh` tests examples from the K web application (k.html).
Run with: `./run-tests.sh -webapp` or `./src/tests/test-webapp-examples.sh`

### Current Status (December 2025)

| Example | Status | Issue |
|---------|--------|-------|
| `Shapes.k` | ❌ FAILING | TypeCheckException - grammar ambiguity |
| `sm.k` | ✅ PASSING | |
| `borges.k` | ❌ FAILING | TypeCheckException - grammar ambiguity |
| `prepost.k` | ✅ PASSING | |
| `Fruits.k` | ✅ PASSING | |
| `lightswitch.k` | ❌ FAILING | TypeCheckException - variable redeclaration |
| `scheduling.k` | ❌ FAILING | TypeCheckException - grammar ambiguity |
| `planning-simple.k` | ❌ FAILING | TypeCheckException - grammar ambiguity |
| `StringDemo.k` | ✅ PASSING | |
| `GravityScience.k` | ✅ PASSING | Expected exception ('assoc' type issue) |
| `DSN_Pass.k` | ✅ PASSING | Expected exception |

**Summary: 6/11 passing (5 failures)**

### Root Cause Analysis

The 4 "grammar ambiguity" failures share a common cause:

**Problem**: In function bodies, `identifier = expression` is being parsed as a `PropertyDecl` 
(property declaration with initialization) instead of an `ExpressionDecl` (equality expression).

**Example from Shapes.k**:
```k
class Angle {
  value : Int
  
  fun eq(other: Angle) : Bool {
    value = other.value    // <-- Parsed as PropertyDecl, not equality!
  }
}
```

The grammar rule for property declaration (line 94 of Model.g4):
```
propertyModifier* Identifier (':' type)? multiplicity? (('='|':=') expression)?
```

When `value = other.value` appears alone in a function body:
- Parser matches it as `PropertyDecl` (name=value, init=other.value)
- Type checker sees PropertyDecl returns UnitType
- Function expects BoolType return → TypeCheckException

**Why `a = b && b = c` works but `value = other.value` doesn't**:
- `a = b && b = c` starts with an identifier but cannot match PropertyDecl due to `&&`
- Falls back to BinOp3Exp where `=` is correctly interpreted as equality (EQ → BoolType)
- Single `value = other.value` fully matches PropertyDecl syntax

### Affected Examples and Their Patterns

| Example | Function | Issue Pattern |
|---------|----------|---------------|
| `Shapes.k` | `eq(other: Angle)` | `value = other.value` |
| `borges.k` | `meets(e: Event)` | `t2 = e.t1` |
| `scheduling.k` | `meets(e: Event)` | `endTime = e.startTime` |
| `planning-simple.k` | `meets(e: Event)` | `t2 = e.t1` |
| `lightswitch.k` | `isOn(state: State/Int)` | `state = on`, `state = 1` (same grammar issue) |

### Historical Note

These examples have existed since 2015 with this syntax. The type checker validation for function 
return types was added/enhanced later. The test-webapp-examples.sh script (created Dec 7, 2025) 
was incorrectly detecting these as passing due to grep pattern matching issues.

### Potential Fixes

1. **Fix the examples** (recommended short-term): Change function bodies to use explicit equality
   ```k
   fun eq(other: Angle) : Bool {
     this.value = other.value    // 'this.' prevents PropertyDecl match
   }
   // OR
   fun eq(other: Angle) : Bool {
     (value = other.value)       // Parentheses force expression parsing
   }
   ```

2. **Grammar change** (complex): Modify grammar to disambiguate property declarations from 
   equality expressions in function bodies

3. **Parser context awareness**: Make parser context-aware so that inside function bodies,
   `identifier = expression` is parsed as equality, not property declaration
