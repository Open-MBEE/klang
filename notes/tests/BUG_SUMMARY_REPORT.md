# K Language Test Analysis - Bug Summary Report
Generated: 2025-01-07

## Executive Summary

Analyzed 216 test files in src/tests/. Found 12 significant bugs or limitations.

## Critical Bugs (Silent Wrong Results)

### 1. undeclared_variable_error.k - TYPE CHECKER ACCEPTS UNDECLARED VARIABLES
- **File**: `x < y` with no declarations
- **Expected**: ERROR (undeclared variables)
- **Actual**: SAT with arbitrary values for x, y
- **Impact**: Type checker silently accepts invalid code
- **Severity**: HIGH

### 2. external6.k - CONSTRAINT VIOLATION ACCEPTED
- **File**: `req x > 0.0` with external function
- **Actual**: x = 0.00 returned, violates req!
- **Verification says**: "All hard constraints satisfied" (LIE)
- **Impact**: Wrong answers with external functions
- **Severity**: CRITICAL

### 3. external2.k - WRONG EXTERNAL FUNCTION VALUES
- **Scenario**: `x : Real; y = sqrt(x); req y > 0`
- **Actual**: x=0.5, y=2.0 (sqrt(0.5) should be ~0.707!)
- **Impact**: External functions with symbolic args return garbage
- **Severity**: CRITICAL

### 4. external3.k - IMPORT BREAKS MATH FUNCTIONS
- **Scenario**: `import java.lang.Math; y = Math.sqrt(100.0)`
- **Actual**: y=2.0 (should be 10.0!)
- **Impact**: Import statement corrupts Math.sqrt results
- **Severity**: CRITICAL

### 5. as2.k - TYPE CHECKER ACCEPTS INVALID CASTS
- **Scenario**: Cast between unrelated classes B and C
- **Actual**: Type checker accepts it
- **Expected**: Type error (no relationship between B and C)
- **Severity**: MEDIUM (type safety hole)

### 6. regex1-7.k - REGEX MATCHING BROKEN (7 of 8 tests fail)
- **Pattern**: All regex tests return UNSAT
- **Actual**: canMatch("hello", ".*") returns false
- **Impact**: Regex constraints are fundamentally broken
- **Severity**: HIGH (feature doesn't work)

## Known Limitations (Expected Behavior)

### 7. cegar1.k - EXTERNAL FUNCTIONS NOT EVALUATED
- **Scenario**: `y = Math.abs(x - 5)` with symbolic x
- **Actual**: x=0, y=0 (abs not evaluated, y should be 5)
- **Impact**: External functions with symbolic args = identity
- **Note**: Partially known, but results are misleading

### 8. external5.k - parseInt NOT IMPLEMENTED
- **Error**: Z3 error on Integer.parseInt
- **Status**: Known missing feature

### 9. besteffort*.k - OPTIMIZATION NOT WORKING
- **Scenario**: maximize(x) with x in [1, 100]
- **Actual**: x=1 (minimum instead of maximum)
- **Impact**: Best effort optimization is broken/ignored
- **Severity**: MEDIUM (feature doesn't work as advertised)

### 10. soft_constraint*.k - SOFT CONSTRAINTS IGNORED
- **Scenario**: Soft constraints parsed but not optimized
- **Impact**: Soft constraints have no effect on solving
- **Severity**: MEDIUM (feature doesn't work)

### 11. opaque_uninterpreted1.k - Z3 NAME COLLISION
- **Error**: "function 'y' already declared"
- **Impact**: Opaque functions can collide with variable names
- **Severity**: LOW (workaround: rename variable)

### 12. mixed_external2.k - EXTERNAL TRIG FUNCTION ERROR
- **Error**: "invalid function application, arguments missing"
- **Status**: Expected UNKNOWN, documented limitation

## Tests with Baseline Mismatches (Need Investigation)

These tests show "baseline mismatch: smt" but outcomes are correct:
- Many tests have SMT generation differences
- Most appear cosmetic (comment changes, etc.)

## Improvements Found (CEGAR/Heap Working Better)

- **heap_chain5.k**: Previously UNSAT, now SAT (CEGAR improved)
- **heap_linked_list.k**: Outcome changed UNSAT→SAT (baseline needs update)
- **duration_hms1.k**: Was "not implemented", now works

## Test Categories Summary

| Category | Count | Status |
|----------|-------|--------|
| ex_* (examples) | 31 | All PASS |
| inheritance* | 12 | All PASS |
| string* | 9 | All PASS |
| testsmt* | 20 | All PASS |
| lambda* | 8 | All PASS |
| ktc_* | 4 | All PASS (expect ERROR) |
| unsat* | 6 | All PASS (expect UNSAT) |
| heap_* | 6 | Mixed (improvements found) |
| external* | 7 | BROKEN (4 have wrong results) |
| regex* | 8 | BROKEN (7 fail) |
| besteffort* | 2 | LIMITED (optimization broken) |
| soft_constraint* | 5 | LIMITED (ignored) |

## Recommendations

### For Immediate Fixes
1. **undeclared_variable_error.k** - Fix type checker to reject undeclared vars
2. **external6.k** - Debug why constraint verification passes with violation
3. **as2.k** - Fix type checker for unrelated class casts

### For Feature Completion  
1. Implement actual regex constraint solving
2. Implement soft constraint optimization
3. Fix external function evaluation for symbolic args

### For Baselines
1. Update heap_linked_list.k baseline (outcome improved)
2. Add @expected annotations to tests missing them
3. Consider stricter baseline format to catch semantic changes

## Files Analyzed: 216
## Bugs Found: 12 (5 critical, 3 high, 4 medium)
