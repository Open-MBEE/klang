# K Language Test Analysis - Master Bug Summary
*Updated continuously during analysis*

## Analysis Status
- Total tests: 216
- Analyzed: 216 (100%)
- Critical bugs found: 7
- High severity: 4
- Medium severity: 3
- Cosmetic only: ~150+ tests

## Critical Bugs (Wrong Results Silently Accepted)

### 1. as2.k - Type Checker Accepts Cast Between Unrelated Classes
- **File**: as2.k
- **Issue**: `(bobj as C)` allowed where B extends A but C is completely unrelated
- **Impact**: Type safety violation, phantom objects created
- **Needs**: Type checker should reject casts between unrelated types

### 2. as2.k - Possible Constraint Value Mismatch
- **File**: as2.k
- **Issue**: Model shows C(t::5) but constraint requires t=4, yet "all constraints satisfied"
- **Status**: NEEDS DEEPER INVESTIGATION
- **Impact**: Either constraint not enforced OR display bug

### 3. besteffort1.k & besteffort_partial1.k & opt2.k & opt3.k - Optimization Ignored
- **Files**: besteffort1.k, besteffort_partial1.k, opt2.k, opt3.k
- **Issue**: `maximize`/`minimize` directives are parsed but completely ignored
- **Evidence**:
  - besteffort1.k: x=1 (minimum) when maximize x should give ~50
  - opt2.k: maximize profit gives profit=80, but optimal is 800
  - opt3.k: maximize speed gives speed=43, but optimal is 100
- **Impact**: Optimization feature is broken
- **Severity**: CRITICAL - advertised feature doesn't work

### 4. cegar1.k - External Function Not Evaluated
- **File**: cegar1.k
- **Issue**: `java.lang.Math.abs(x - 5)` returns 0 instead of |0-5|=5
- **Evidence**: y=0 but constraint y<=3 passed, when y should be 5 (VIOLATION)
- **Impact**: External functions with symbolic args return garbage
- **Severity**: CRITICAL - silent wrong results

### 5. external2.k - sqrt Returns Wrong Value (Without Explicit Constraint)
- **File**: external2.k, external3.k
- **Issue**: sqrt(0.5) returns 2.0 instead of ~0.707; sqrt(100.0) returns 2.0 instead of 10.0
- **Root Cause**: When there's NO explicit constraint on the result, external functions return garbage
- **Works When**: Result has explicit constraint (e.g., `req result = 4.0`)
- **Pattern**: external1.k, external4.k, external7.k have explicit constraints and work; external2.k, external3.k don't and fail
- **Impact**: External functions only work if you know the answer in advance!

### 6. external3.k - Import Statement Breaks External Functions (CLARIFIED)
- **File**: external3.k
- **Issue**: With import, Math.sqrt(100.0) returns 2.0 instead of 10.0
- **Note**: This is actually the same root cause as #5 - no explicit constraint
- **Note**: Python externals work correctly! python_external2.k does math.sqrt(100.0)=10.0
- **Note**: mixed_external1.k works because it has `req javaResult = 4.0`

### 7. external6.k - Constraint Violation Silently Accepted
- **File**: external6.k
- **Issue**: x=0.00 returned but req x > 0.0 should fail!
- **Evidence**: Verification says "All constraints satisfied" - LIE
- **Impact**: Constraint verification is broken for some cases

## High Severity (Feature Broken)

### 1. SMT Expression Leaking in Model Output
- **File**: array_comprehensive_test.k (ArrayBoolValueTest class)
- **Issue**: Array[Int, Bool] displays raw SMT `(store ((as const ...` instead of evaluated values
- **Impact**: Model output unreadable, may indicate incomplete evaluation
- **Component**: Model pretty-printer

### 2. Regex Matching Always Returns False (7 of 8 tests)
- **Files**: regex1.k through regex_solver1.k
- **Issue**: `String.matches(pattern)` always returns false/UNSAT
- **Evidence**: "5".matches("[0-9]") → UNSAT (should be SAT)
- **Impact**: Regex constraint solving completely non-functional

### 3. external5.k - parseInt Not Implemented
- **File**: external5.k
- **Issue**: Integer.parseInt not supported, returns UNKNOWN
- **Impact**: Missing feature

## Medium Severity (Limitations/Edge Cases)

### 1. Set Display Shows Incomplete Values
- **File**: set_comprehensive_test.k
- **Issue**: Set display shows only some elements (e.g., Set{1,2,3,4,5} shows as Set(4,5))
- **Impact**: Model output misleading, hard to verify correctness manually
- **Note**: Solver verification passes, so sets work at SMT level

### 2. Missing @expected Annotations
- **Files affected**: Many tests (array_test.k, as1.k, besteffort*.k, bitvec*.k, etc.)
- **Issue**: Test has comment about expected outcome but no `@expected()` annotation
- **Impact**: Test runner can't validate outcome automatically

### 2. Soft Constraints Ignored
- **Files**: soft_constraint1-5.k
- **Issue**: `soft req` is parsed but has no effect on solving
- **Evidence**: soft_constraint1.k has x=0, y=43 but soft req x=y and x+y=50 both violated
- **Impact**: Soft constraint feature doesn't work

## Low Severity (Cosmetic/Minor)

### Many Tests Have Cosmetic Baseline Diffs
- Most tests that show "baseline mismatch: smt" have only cosmetic SMT differences
- Outcome is correct, just SMT text changed
- Need to regenerate baselines after review

## Tests with Issues (Quick Reference)

| Test File | Issue Summary | Severity |
|-----------|---------------|----------|
| array_comprehensive_test.k | SMT leak in Bool array model; missing @expected | HIGH/MED |
| array_test.k | Missing @expected | MED |
| array_types.k | Clean - cosmetic baseline diff only | LOW |
| as1.k | Missing @expected ERROR (test works correctly) | MED |
| as2.k | Type checker accepts unrelated cast; possible value mismatch | CRITICAL |
| besteffort1.k | maximize x ignored (x=1 not ~50) | CRITICAL |
| besteffort_partial1.k | maximize ignored (sum=43 not 150) | CRITICAL |
| bitvec_test.k | Clean - all operations correct | LOW |
| bitvector_real_conversions.k | Cosmetic baseline diff | LOW |
| bitvector_width_conversions.k | Cosmetic baseline diff | LOW |
| cast_test.k | Cosmetic baseline diff | LOW |
| cegar1.k | External Math.abs not evaluated (y=0 not 5) | CRITICAL |
| ctor_syntax_test.k | Cosmetic baseline diff | LOW |
| diamond_*.k (6 files) | Cosmetic baseline diffs | LOW |
| duration_*.k (2 files) | Cosmetic baseline diffs | LOW |
| empty_class.k | Cosmetic baseline diff | LOW |
| external2.k | sqrt(0.5)=2.0 instead of 0.707 | CRITICAL |
| external3.k | import breaks sqrt (returns 2.0 not 10.0) | CRITICAL |
| external5.k | parseInt not implemented (UNKNOWN) | HIGH |
| external6.k | x=0.00 passes req x>0.0 - CONSTRAINT VIOLATION! | CRITICAL |
| opt2.k | maximize profit gives 80, optimal is 800 | CRITICAL |
| opt3.k | maximize speed gives 43, optimal is 100 | CRITICAL |
| opaque_uninterpreted1.k | Z3 name collision | HIGH |
| set_comprehensive_test.k | Display shows incomplete set values | MEDIUM |
| regex1-7.k | matches() always returns false/UNSAT | HIGH |
| regex3.k | Works only because it tests false match | N/A |
| soft_constraint1-5.k | soft req completely ignored | MEDIUM |

## Checklist Items Discovered During Analysis

1. Check for raw SMT expressions leaking into model output (patterns: `(store`, `(as const`, `(ite`, etc.)
2. Check if file has "Expected:" comment but no @expected annotation

## Test Categories Summary

| Category | Count | Status |
|----------|-------|--------|
| array*.k | 3 | 1 HIGH issue (SMT leak) |
| as*.k | 2 | 1 CRITICAL issue (cast bug) |
| besteffort*.k | 2 | CRITICAL (optimization ignored) |
| bitvec*.k | 3 | All working correctly |
| cegar1.k | 1 | CRITICAL (external function bug) |
| diamond*.k | 6 | All working (cosmetic diffs) |
| duration*.k | 2 | All working (cosmetic diffs) |
| ex_*.k | 31 | All working (cosmetic diffs) |
| external*.k | 7 | 3 CRITICAL bugs, 1 HIGH |
| factorial*.k | 2 | All working |
| heap*.k | 6 | All working (1 improvement) |
| inheritance*.k | 12 | All working (cosmetic diffs) |
| ktc_*.k | 4 | All working correctly |
| ktypechecker_test.k | 1 | UNSAT by design (test bug) |
| lambda*.k | 8 | All working (cosmetic diffs) |
| mixed_external*.k | 2 | 1 working, 1 expected UNKNOWN |
| mutual_recursion*.k | 6 | All working |
| object_bounds*.k | 5 | All working |
| opaque*.k | 5 | 1 HIGH issue (name collision) |
| opt*.k | 4 | 2 CRITICAL (optimization ignored) |
| python_external*.k | 3 | All working correctly! |
| regex*.k | 8 | 7 HIGH (matches() broken) |
| set*.k | 2 | 1 MEDIUM (display incomplete) |
| soft_constraint*.k | 5 | All MEDIUM (ignored) |
| string*.k | 9 | All working correctly |
| testsmt*.k | 20 | All working |
| testsets*.k | 6 | All working |
| type_inference*.k | 8 | 1 test bug |
| unsat*.k | 6 | All working correctly |
| Other | ~30 | All working |

## Script Improvements Made

| Change | Reason | Test that revealed it |
|--------|--------|----------------------|
| Initial 13 checks | Starting point | N/A |
| TODO: SMT leak detection | Raw SMT in model output | array_comprehensive_test.k |
| TODO: Comment vs annotation check | Missing @expected | array_comprehensive_test.k |

## Analysis Log

### Session: 2026-01-23
Full pass through all 216 tests completed.

**Critical Issues Found:**
1. Optimization (maximize/minimize) completely ignored - affects besteffort*.k, opt2.k, opt3.k
2. External functions without explicit constraints return garbage - affects external2.k, external3.k
3. External6.k has constraint violation (x=0.00 passes req x>0.0)
4. Type cast between unrelated classes allowed (as2.k)

**High Issues Found:**
1. Regex matches() always returns false (7 tests)
2. SMT expression leaking in array model output
3. parseInt not implemented
4. Z3 name collision in opaque_uninterpreted1.k

**Medium Issues Found:**
1. Soft constraints completely ignored
2. Set display shows incomplete values
3. Many tests missing @expected annotations

**Working Well:**
- Python externals (math.sqrt etc) work correctly!
- String operations all work
- Bitvector operations all work
- Type conversions all work
- Lambda expressions work
- Heap analysis works (CEGAR improvements)
