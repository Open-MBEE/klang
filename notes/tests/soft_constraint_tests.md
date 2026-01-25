# soft_constraint1.k - soft_constraint5.k Analysis (Batch)

## Purpose
Test soft constraint syntax (soft req) - preferences that should be satisfied when possible.

## Current State
- Expected: SAT with soft constraints satisfied where possible
- Actual: SAT but soft constraints completely ignored
- Status: **MEDIUM SEVERITY - Feature Not Working**

## Evidence from soft_constraint1.k

**Constraints**:
```k
req x >= 0, x <= 100  -- hard
req y >= 0, y <= 100  -- hard
soft req x + y = 50   -- soft preference
soft req x = y        -- soft preference
```

**Result**: x = 0, y = 43

**Analysis**:
- Hard constraints: ✓ (0 >= 0, 0 <= 100, 43 >= 0, 43 <= 100)
- Soft constraint 1: x + y = 0 + 43 = 43 ≠ 50 ✗
- Soft constraint 2: x = y → 0 ≠ 43 ✗

**Expected**: x = 25, y = 25 would satisfy BOTH soft constraints!

## Issue: SOFT CONSTRAINTS IGNORED

**Problem**: Soft constraints are parsed but have no effect on solving.
**Impact**: The `soft req` feature is non-functional.
**Evidence**: Solver returns arbitrary values that don't try to satisfy soft constraints.

## Severity: MEDIUM
- Feature advertised but doesn't work
- Hard constraints still work, so basic functionality OK

## Recommendations
1. Check if Z3 Optimize API is being used for soft constraints
2. Soft constraints should add to optimization objective
3. Add test assertions to verify soft constraint satisfaction
