# Type Inference via Z3: Design Investigation

## Current State (December 2025)

### ✅ FIXED: Undeclared Variable Bug

**Bug**: Top-level bare expressions like `x < y` were silently ignored (passed type checking but produced nothing).

**Fix** (commit 442ef6c): Removed `ExpressionDecl` from `inferUndeclaredTypes()` collection. Now bare expressions properly error with "x not found in scope".

**Test**: `src/tests/undeclared_variable_error.k`

### Variable Declaration Requirements

| Context | Example | Result | Notes |
|---------|---------|--------|-------|
| Top-level bare expression | `x < y` | ❌ Type error | "x not found in scope" (FIXED) |
| Top-level `req` constraint | `req x > 0` | ✅ Type checks | Infers x as numeric |
| Top-level `x = y` | `x = y` | ❌ Type error | Parsed as property declaration; `y not found` |
| In class | `class A { x < y }` | ❌ Type error | `x not found in scope` |
| Top-level with decls | `x : Int` + `req x < y` | ✅ Works | Full solution |

### Key Insight

The type checker now has **consistent behavior**:
- ConstraintDecl (`req x > 0`) allows type inference for undeclared variables
- ExpressionDecl (bare `x < y`) requires declared variables
- Expressions in classes require declared variables

### Original Proposal (from TYPE_INFERENCE.md)

Encode types as Z3 constraints instead of a separate type checking pass:

```k
// Would work without explicit declarations:
req x > 0           // Infers x : Int (or Real)
req y = x + 1       // Infers y : Int (same as x)
req name = "hello"  // Infers name : String
```

**Type constraints would be generated**:
- `x > 0` → `type(x) ∈ {Int, Real}`
- `a = b` → `type(a) = type(b)`
- `f(x)` where `f : A → B` → `type(x) = A ∧ type(f(x)) = B`

### Why We Didn't Proceed

No explicit notes found, but likely reasons:
1. **Complexity**: Requires rethinking the entire compilation pipeline
2. **Z3 Sort Constraints**: Z3 doesn't directly support "sort variables" - would need meta-level encoding
3. **Error Messages**: Type errors from Z3 unsatisfiability are harder to make user-friendly
4. **Incremental Risk**: Large change to a working system

### Considerations for Now

**If we're going to do this, now is the time because:**
1. Major changes already underway (external function support, etc.)
2. TypeChecker.scala is ~1900 lines and complex
3. Philosophy aligns with K being constraint-based

**Open questions:**
1. Should bare `x < y` at top-level declare `x` and `y` implicitly?
2. What's the default type when multiple are possible? (`x > 0` could be Int or Real)
3. How to handle `x = y` ambiguity (equality vs property declaration)?
4. Can we provide good error messages from Z3 unsatisfiability?

### Possible Approach

1. **Phase 1**: Make top-level constraints behave consistently
   - Either error on undeclared vars (like in classes)
   - Or infer declarations (Z3-based approach)

2. **Phase 2**: If Z3-based, encode sort constraints
   - Use uninterpreted functions: `(declare-fun type (Var) Sort)`
   - Assert constraints: `(assert (= (type x) Int))`
   
3. **Phase 3**: Handle ambiguity
   - Default type rules (Int preferred over Real?)
   - Syntax to distinguish `x = y` (property) vs `x == y` (constraint)
