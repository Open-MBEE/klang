# cegar1.k Analysis

## Purpose
Tests CEGAR-style refinement with external functions (Math.abs).

## Current State
- Expected: SAT (comment says PASSED)
- Actual: SAT with x=0, y=0
- Status: **CRITICAL BUG - External Function Not Evaluated**

## Checklist Results
- [x] Read actual K file
- [x] Manually verified constraints
- [x] Found critical bug

### Code Analysis
```k
x : Int
req x >= 0
req x <= 10
var y : Int = java.lang.Math.abs(x - 5)
req y <= 3
```

### Expected Behavior
- y = |x - 5|
- With x ∈ [0, 10], valid x values where |x-5| <= 3 are x ∈ [2, 8]
- Any x in [2, 8] should give y ∈ [0, 3]

### Actual Behavior
- x = 0
- y = 0 (WRONG - should be |0-5| = 5)
- Yet "All 7 hard constraints satisfied"

### Issue: EXTERNAL FUNCTION NOT EVALUATED (CRITICAL)

**Problem**: `java.lang.Math.abs(x - 5)` is not being evaluated.
- Expected: y = |x - 5|
- Actual: y = 0 (arbitrary value, ignores abs)

**Impact**: External functions with symbolic arguments return garbage.

**Evidence**:
- If y really = |x - 5| = |0 - 5| = 5
- Then y <= 3 is FALSE (5 > 3)
- Constraint should be violated!

**Root Cause**: External functions are likely not being called during SMT solving. The `abs` call is probably encoded as an uninterpreted function or identity.

## Severity: CRITICAL
External function integration is broken for symbolic arguments.

## Recommendations
1. Investigate how external functions are encoded in SMT
2. For Math.abs specifically, could use SMT definition: (ite (< x 0) (- x) x)
3. Add runtime check that y actually equals abs(x-5) in model verification
