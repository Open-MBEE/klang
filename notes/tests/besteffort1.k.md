# besteffort1.k Analysis

## Purpose
Test best-effort solving with `maximize x` optimization objective.

## Current State
- Expected: SAT with maximized x (no @expected annotation)
- Actual: SAT with x=1 (MINIMUM, not maximum!)
- Status: **CRITICAL BUG - Optimization Ignored**

## Checklist Results
- [x] Read actual K file
- [x] Verified constraints manually
- [x] Found critical bug

### Constraint Analysis
```k
req x >= 1, x <= 100
req y >= 1, y <= 100
req z >= 1, z <= 100
req x + y + z <= 150
req x * 2 <= y + z  (i.e., y + z >= 2x)
maximize x
```

**Expected maximum x**: ~50
- If x = 50, then y + z >= 100 (from constraint x*2 <= y+z)
- And x + y + z <= 150, so y + z <= 100
- Therefore y + z = 100, satisfying both constraints

**Actual result**: x = 1 (the minimum!)

### Model Output
```
BestEffort1(x::1, y::20, z::39)
```
- x = 1 (WRONG - should be ~50)
- y = 20
- z = 39  
- Check: 1 + 20 + 39 = 60 <= 150 ✓
- Check: 1 * 2 = 2 <= 20 + 39 = 59 ✓

Constraints are satisfied but x is not maximized!

## Issue: OPTIMIZATION DIRECTIVE IGNORED (CRITICAL)

**Problem**: `maximize x` is parsed but completely ignored by the solver.
**Impact**: Best-effort/optimization features are broken.
**Severity**: CRITICAL - feature advertised but doesn't work

**Evidence**:
- Objective: maximize x with x ∈ [1, 100]
- Result: x = 1 (minimum possible value)
- Should be: x ≈ 50 (maximum given constraints)

## Recommendations
1. Investigate why `maximize` directive is not being applied
2. Check if Z3 Optimize API is actually being used
3. Add @expected annotation with specific expected value
4. Consider adding test assertions like `req x >= 40` to catch this regression
