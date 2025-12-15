# K-Based Type Checker

## Overview

The K-based type checker (`KTypeChecker.scala`) is an alternative type checking approach that encodes type checking as a K constraint satisfaction problem. Instead of a traditional procedural type checker, it:

1. Generates a K program where type variables are represented as integer constraints
2. Runs the K solver to find a solution
3. Interprets the solution as inferred types

**Note**: The K-based type checker is currently experimental because it spawns a subprocess to run K, which is slow. The traditional type checker remains the default. Use `-ktc` or one of the `-tc-*` mode flags to enable the K-based checker.

## Type Checking Modes

The K-based type checker supports four modes based on two boolean options:

| Mode | Flag | Declarations Required | Ambiguous Types | Description |
|------|------|----------------------|-----------------|-------------|
| **Strict** | `-tc-strict` | Yes | No | All variables must be declared, types fully determined |
| **InferredDecls** | `-tc-inferred` | No | No | Variables can be undeclared, types must be unambiguous |
| **AmbiguousTypes** | `-tc-ambiguous` | Yes | Yes | Declarations required, types can have multiple valid values |
| **FullyFlexible** | `-tc-flexible` | No | Yes | No declarations needed, types can be ambiguous |

### Mode Examples

**Strict mode** (`-tc-strict`):
```k
x : Int = 10    -- OK: explicit declaration
req y > 0       -- ERROR: y not declared
```

**InferredDecls mode** (`-tc-inferred`, default for K-based):
```k
x : Int = 10    -- OK
req y > 0       -- OK: y inferred as numeric
```

**AmbiguousTypes mode** (`-tc-ambiguous`):
```k
x : Int         -- OK
req x > 0       -- OK: x could be Int or Real (solver picks one)
req y > 0       -- ERROR: y not declared
```

**FullyFlexible mode** (`-tc-flexible`):
```k
req x > 0       -- OK: x inferred as numeric (Int or Real)
req y = x + 1   -- OK: y has same type as x
```

## Benefits

- **Unified Approach**: Uses K's constraint solver (Z3) for type inference
- **Leverages Existing Infrastructure**: No new solver needed
- **More Flexible Inference**: Can infer types from any constraint context
- **Debugging**: The generated K program is human-readable

## Usage

### Generate Type Check Program Only

```bash
./export/k file.k -ktc-gen
```

This generates the K program for type checking without running the solver. Useful for debugging and understanding how types are encoded.

### Run K-Based Type Checker

```bash
./export/k file.k -ktc           # Default mode (InferredDecls)
./export/k file.k -tc-strict     # Strict mode
./export/k file.k -tc-inferred   # InferredDecls mode
./export/k file.k -tc-ambiguous  # AmbiguousTypes mode
./export/k file.k -tc-flexible   # FullyFlexible mode
```

## How It Works

### Type Encoding

Types are represented as integers:
- `0` = Bool
- `1` = Int
- `2` = Real
- `3` = String
- `4` = Char
- `5` = Unit
- `6` = Any
- `10+` = User-defined types (classes)

### Constraints Generated

For each variable `v`, a type variable `_ty_v` is created:

```k
_ty_v : Int
req _ty_v >= 0
```

Type constraints are generated from:

1. **Explicit declarations**: `x : Int` → `req _ty_x = 1`
2. **Literals**: `x = 42` → `req _ty_x = 1` (Int)
3. **Same type**: `x = y` → `req _ty_x = _ty_y`
4. **Numeric operations**: `x + y` → `req (_ty_x = 1 || _ty_x = 2)` 
5. **Boolean operations**: `x && y` → `req _ty_x = 0`
6. **Comparisons**: `x > y` → `req _ty_x = _ty_y` and both must be comparable

### Example

Input K program:
```k
a = 42
b = a + 10
c = "hello"
req b > 0
```

Generated type check program:
```k
_ty_a : Int
_ty_b : Int
_ty_c : Int

req _ty_a = 1           -- from literal 42
req _ty_b = _ty_a       -- from b = a + 10
req _ty_a = 1           -- from + 10
req _ty_c = 3           -- from "hello"
req (_ty_b = 1 || _ty_b = 2)  -- comparison requires numeric
```

If the K solver finds a solution, type checking passes. If UNSAT, there's a type error.

### Type Errors

When type constraints are contradictory, the K solver returns UNSAT:

```k
x : String = "hello"
y = x + 10    -- Type error: String + Int
```

Generated constraints include:
```k
req _ty_x = 3   -- String
req _ty_x = 1   -- from + 10 (Int literal)
```

These are contradictory, so the solver returns UNSAT, indicating a type error.

## Implementation

The implementation is in `src/k/frontend/KTypeChecker.scala`:

- `KTypeContext`: Manages type IDs and generates K program
- `analyzeExpression`: Recursively generates constraints from expressions
- `generateKProgram`: Outputs the K program source
- `runKTypeCheck`: Executes K and parses results

## Future Work

1. **Better error messages**: Currently only reports "type error" - could extract which constraints failed
2. **Subtyping**: Currently types must match exactly - could add subtype constraints
3. **Polymorphism**: No generic type support yet
4. **Type augmentation**: Use inferred types to simplify the original program before solving

## Related Files

- `src/k/frontend/KTypeChecker.scala` - Implementation
- `src/k/frontend/TypeConstraints.scala` - Original constraint-based inference
- `docs/features/TYPE_INFERENCE.md` - General type inference documentation
- `docs/investigations/TYPE_INFERENCE_VIA_Z3.md` - Design investigation
