# Type Inference in K Language

## Overview

K now supports type inference for property declarations. When a property has an initialization expression, the type can be omitted and will be automatically inferred from the expression.

## Syntax

### Before (explicit types required)
```k
class Example {
  count : Int = 10
  name : String = "hello"
  active : Bool = true
}
```

### After (types can be inferred)
```k
class Example {
  // Explicit types still work
  count : Int = 10
  
  // Types can now be inferred from initialization expressions
  inferredCount = 10          // Inferred as Int
  inferredName = "hello"      // Inferred as String  
  inferredActive = true       // Inferred as Bool
  inferredReal = 3.14         // Inferred as Real
  
  // Types can be inferred from expressions
  sum = count + 5             // Inferred as Int (from binary expression)
  greeting = name + " world"  // Inferred as String
}
```

### Inference from Function Calls

Types can also be inferred from function return types:

```k
class A

fun foo() : A {
    a : A
    return a
}

// Type inferred from function return type
aa = foo()   // Inferred as type A
```

## Rules

1. **Explicit types always work**: You can still specify types explicitly as before.

2. **Inference requires initialization**: If you omit the type, you MUST provide an initialization expression:
   ```k
   x = 10        // OK - type inferred as Int
   y : Int       // OK - explicit type, no initialization needed
   z             // ERROR - no type and no initialization
   ```

3. **Type checking still applies**: If you provide both a type and an initialization expression, the type checker verifies they match:
   ```k
   x : Int = 10           // OK
   y : Int = "hello"      // ERROR - type mismatch
   ```

4. **Works in all contexts**:
   - Top-level properties
   - Class properties
   - Local variables in function bodies
   - Block expressions

## Implementation Details

The type inference is implemented as "local type inference" - the type is inferred from the initialization expression at the point of declaration. This is similar to Scala's `val x = 10` or Kotlin's `val x = 10`.

### Files Modified

- `src/grammar/Model.g4` - Made type annotation optional in property declarations
- `src/k/frontend/AbstractSyntax.scala` - Changed `PropertyDecl.ty` to `Option[Type]` and added `inferredType` field
- `src/k/frontend/KScalaVisitor.scala` - Updated visitor to handle optional types
- `src/k/frontend/TypeChecker.scala` - Added type inference logic
- Various other files updated to handle `Option[Type]`

### How It Works

1. During parsing, if no type is specified, `PropertyDecl.ty` is `None`
2. During type checking, if `ty` is `None`:
   - If there's an initialization expression, infer the type using `getExpType()`
   - Set `PropertyDecl.inferredType` to the inferred type
   - If there's no initialization expression, report an error
3. The `PropertyDecl.getType` method returns either the explicit type or the inferred type
4. The `PropertyDecl.getTypeOrError` method throws an exception if neither is available

## Example Test File

See `src/tests/type_inference_test.k` for a working example demonstrating the feature.

## Bare Expressions as Implicit Constraints

As of December 2025, bare expressions like `x < y` are now treated as implicit constraints, equivalent to `req x < y`. Combined with type inference for undeclared variables, this allows very concise K programs:

```k
// These are now equivalent:
x < y
req x < y
```

When `x` and `y` are undeclared, the type checker infers their types (defaulting to Real) and creates synthetic property declarations.

## Type Inference Modes

K type checking can be conceptualized as having four modes based on two boolean options:

| Mode | Declarations Required | Types Must Be Unambiguous | Status |
|------|----------------------|---------------------------|--------|
| **Strict** | Yes | Yes | Traditional - all tests pass |
| **Inferred Decls** | No | Yes | Current default for top-level |
| **Ambiguous Types** | Yes | No | Future work |
| **Fully Flexible** | No | No | Future work |

### Current Behavior

- **Top-level**: Variables can be undeclared; types inferred from context (defaults to Real)
- **Class members**: Variables must be declared explicitly
- **Type ambiguity**: Currently resolved by defaulting to Real; future work to use constraint solving

### Future: Four-Mode Configuration

A potential configuration could be:

```k
@options(requireDeclarations = false, requireUnambiguousTypes = false)
x < y    // x, y inferred as Real (or any numeric type)
```

## Future Work: Type Constraints in Z3

The current type checker (`src/k/frontend/TypeChecker.scala`) is large and complex (~1500 lines). A cleaner approach would be to integrate type inference with the constraint solver by making types part of the constraint problem.

### Motivation

Currently, `=` in K is an equality constraint, not an assignment. However, the type checker treats property declarations with `=` specially for type inference:

```k
x = 0      // Type inferred from RHS - works  
x > 0      // Type inferred from context - NOW WORKS (defaults to Real)
```

Semantically, both are constraints. The second implies `x` must be numeric just as much as the first.

### Proposed Approach

Instead of a separate type checking pass, encode types as Z3 constraints:

1. **Type variables**: Each undeclared variable gets a type variable
2. **Type constraints**: Expressions generate type constraints:
   - `x > 0` → `type(x) ∈ {Int, Real}`
   - `a = b` → `type(a) = type(b)`
   - `f(x)` where `f : A → B` → `type(x) = A ∧ type(f(x)) = B`
3. **Z3 solves both**: The constraint solver determines both values AND types

### Benefits

- **Unified approach**: Types and values solved together
- **More flexible inference**: Can infer from any constraint, not just `=`
- **Underspecified types**: `a = b` could have any type (defaulting to Int)
- **Simpler codebase**: Remove the large TypeChecker.scala
- **Constraint-based errors**: Type errors become unsatisfiable constraints

### Example

```k
// With type constraints, this could work without declarations:
req x > 0           // Infers x : Int (or Real)
req y = x + 1       // Infers y : Int (same as x)
req name = "hello"  // Infers name : String
req a = b           // Infers a, b : Int (default) with a = b
```

### Challenges and Research Directions

1. **Ambiguous Types**: When is `x < y` comparing Ints vs Reals? Options:
   - Default to a specific type (current: Real)
   - Require explicit declarations when ambiguous
   - Use constraint-based type resolution (CEGAR-like approach)

2. **CEGAR for Types**: Counterexample-Guided Abstraction Refinement could help:
   - Start with most general types
   - If constraints are unsatisfiable, refine type assumptions
   - Iterate until solution found or proven impossible

3. **Difficult Cases** (TODO: create test cases):
   - Cyclic type dependencies: `f(x) = y, g(y) = x`
   - Overloaded operators: `a + b` where `+` works on Int, Real, String
   - Polymorphic functions: `identity(x) = x`
   - Subtyping: `class A extends B` with type constraints

### Reference

See `src/tests/type_inference_constraint.k` for a test case that raises this design question.

