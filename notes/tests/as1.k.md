# as1.k Analysis

## Purpose
Tests type checking for cast between unrelated classes when accessing a field that doesn't exist on the target type.

## Current State
- Expected: ERROR (no @expected, but baseline shows ERROR behavior)
- Actual: ERROR - "[TypeChecker] Given expression does not type check: (bobj as C).y"
- Status: PASS

## Checklist Results
- [x] Read actual K file
- [x] Understood the test purpose
- [x] Verified correct behavior

### Code Analysis
```k
class B extends A { ... }  // B has field y (inherited from A)
class C                     // C has NO field y
bobj : B
req (bobj as C).y = 4       // Cast B to C, then access y
```

The type checker correctly rejects this because:
1. bobj is type B
2. Cast to C (which has no field y)
3. Access .y fails - C doesn't have y

### Issues Found
1. **Missing @expected annotation** - Should have `@expected ERROR`

## Recommendations
- Add `@expected ERROR` annotation
