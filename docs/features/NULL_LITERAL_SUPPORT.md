# Null Literal Support

## Overview

The K language supports `null` literals for comparing String and reference types. This document describes the implementation and known limitations.

## Syntax

```k
-- Null comparison with String
var s: String
req s != null

-- Null comparison with class reference  
var person: Person
req person = null
```

## Type System

- `NullLiteral` has type `NullType`
- `NullType` is compatible with:
  - `StringType` - Strings can be null
  - `IdentType` (class references) - References can be null
  - Other `NullType` - `null = null` is valid

## SMT Representation

Two constants are declared in the SMT model:

```smt2
(declare-const NULL$ String)        ; For String null comparisons
(declare-const NULL$Ref Ref)        ; For reference null comparisons
(assert (= NULL$Ref (- 1)))         ; Null refs are represented as -1
```

The `BinExp.toSMT` method selects the appropriate constant based on the type of the non-null operand:
- String comparisons use `NULL$`
- Reference comparisons use `NULL$Ref`

## Known Limitations

### Reference Type Invariants

K's type system generates invariants that require reference fields to point to valid objects of the expected type. For example, given:

```k
class RefHolder {
  var ref: StringHolder
  req ref = null  -- This constraint conflicts with the type invariant
}
```

The generated SMT includes:
```smt2
(define-fun RefHolder.inv1 ((this Ref)) Bool
  (deref-isa-StringHolder (RefHolder!ref this))  ; ref must be a valid StringHolder
)
```

This invariant requires `ref` to point to a valid `StringHolder`, which conflicts with `ref = null` (where null = -1). The result is UNSAT.

**Workaround**: Null comparisons work correctly in:
- Function return values
- Local comparisons within functions
- Constraints where the null check is in a disjunction: `req ref = null || ref.field > 0`

### No Short-Circuit Evaluation in SMT

SMT does not perform short-circuit evaluation. In `spouse = null || spouse.name != null`, both sides are evaluated. When `spouse` is null (-1), `spouse.name` attempts to dereference an invalid reference.

## Implementation Files

- `src/k/frontend/AbstractSyntax.scala`:
  - `NullType` case object (type definition)
  - `NullLiteral.toSMT` returns "NULL$"
  - `BinExp.toSMT` handles EQ/NEQ with null operands
  - NULL$ constants declared in `Model.toSMT`

- `src/k/frontend/TypeChecker.scala`:
  - `NullLiteral => NullType` in `getExpType`
  - `areTypesEqual` handles NullType compatibility

## Test File

See `src/tests/null_test.k` for examples of supported null usage patterns.

## Future Work

To fully support nullable reference fields, the type invariant generation would need to be modified to allow null values for reference fields. This would require:

1. Adding an "optional" or "nullable" annotation for properties
2. Modifying invariant generation to check `ref = NULL$Ref || deref-isa-Type(ref)`
3. Adding null-safety checks in property access paths

This is a significant change to K's type system and heap semantics.
