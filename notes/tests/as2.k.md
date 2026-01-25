# as2.k Analysis

## Purpose
Tests type checking for cast between unrelated classes when accessing a field that DOES exist on the target type.

## Current State
- Expected: SAT (baseline)
- Actual: SAT
- Status: **CRITICAL BUG - Multiple Issues**

## Checklist Results
- [x] Read actual K file
- [x] Manually verified constraints
- [x] Found critical bugs

### Code Analysis
```k
class B extends A { z : Int }  // B extends A
class C { t : Int }             // C is UNRELATED to A or B
bobj : B
req (bobj as C).t = 4           // Cast B to C, then access t
```

### Issue 1: TYPE CHECKER SHOULD REJECT (HIGH)
**Problem**: B and C have no inheritance relationship. The cast `bobj as C` should be rejected.
**Why it's wrong**: You cannot cast an object of type B to type C when:
- B is not a subclass of C
- C is not a subclass of B
- They share no common ancestor (other than Object)

In Java/Scala, this would be a compile-time error.

### Issue 2: POSSIBLE CONSTRAINT VALUE MISMATCH (NEEDS INVESTIGATION)
**Problem**: The model shows `C(t::5)` but the constraint says `t = 4`!
**Evidence from output**:
```
Ref 3|C(t::5)      <-- t is 5, not 4!
```
**Constraint**: `req (bobj as C).t = 4`

Yet verification claims "All 8 hard constraints satisfied".

Possibilities:
- The C object displayed isn't what the constraint refers to (phantom object)
- Model display is showing a different field
- There's a bug in constraint enforcement OR display

**Status**: NEEDS DEEPER INVESTIGATION

### Issue 3: PHANTOM OBJECT CREATED
**Problem**: The cast creates a phantom C object (Ref 3) that has no relationship to bobj (Ref 2).
**Evidence**:
- bobj → Ref 2 → B(x::8, y::9, z::10)
- ?? → Ref 3 → C(t::5)

The C object is floating - it's not actually bobj cast to C, it's just a random C object.

## Severity: CRITICAL
- Type safety violation
- Possible constraint violation  
- Semantic confusion

## Recommendations
1. Type checker should reject casts between unrelated classes
2. If casts are intentionally permissive, document the semantics clearly
3. Investigate why t=5 when constraint requires t=4
