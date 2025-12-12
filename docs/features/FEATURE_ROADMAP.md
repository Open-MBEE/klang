# K Language Feature Roadmap

## Overview

This document tracks features that are missing or incomplete in K relative to SMT-LIB2/Z3 capabilities, along with general language features that would improve usability.

## Current Status (December 2025)

### ✅ Fully Supported SMT-LIB2 Features

| Feature | SMT Theory | K Syntax | Notes |
|---------|-----------|----------|-------|
| Integers | QF_LIA | `Int` | Arbitrary precision |
| Reals | QF_LRA | `Real` | Rational arithmetic |
| Booleans | Core | `Bool` | Standard logic |
| Strings | QF_S | `String` | Full string theory |
| Sequences | Seq | `Seq[T]` | Recently integrated |
| Sets | Arrays | `Set[T]` | Via array encoding |
| Tuples | Datatypes | `Tuple(a,b)` | Via Z3 datatypes |
| Quantifiers | Core | `forall`/`exists` | Universal/existential |
| Uninterpreted Functions | UF | Functions | Declared functions |
| Regular Expressions | Regex | `.matches()` | Via string theory |
| Optimization | Optimize | `@soft`, `@minimize` | Soft constraints |
| Time/Duration | - | `Time`, `Duration` | K-specific types |

### ⚠️ Partially Supported Features

| Feature | Issue | Status |
|---------|-------|--------|
| Java Constructor Calls | TypeChecker missing `CtorApplExp` handling | Needs implementation |
| Tuple Constructor | `Tuple(x,y)` syntax not type-checked | Needs implementation |
| Complex Set Operations | Set comparison (`>=`) not SMT-native | Workaround needed |

### ❌ Missing SMT-LIB2/Z3 Features

| Feature | SMT Theory | Priority | Complexity | Notes |
|---------|-----------|----------|------------|-------|
| **Bit Vectors** | QF_BV | High | Medium | Hardware modeling, binary protocols |
| **IEEE Floating Point** | QF_FP | Medium | High | Overflow, NaN, rounding modes |
| **Arrays (direct)** | QF_A | Low | Medium | Currently only used internally |

---

## Feature: Bit Vector Support

### Motivation

Bit vectors are essential for:
- Modeling hardware and digital circuits
- Binary protocol specifications
- Cryptographic algorithms
- Fixed-width integer overflow behavior
- Bloom filters and hash functions

### SMT-LIB2 Bit Vector Operations

Z3's QF_BV theory provides:

```smt2
; Types
(declare-const x (_ BitVec 32))  ; 32-bit vector
(declare-const y (_ BitVec 32))

; Bitwise operations
(bvand x y)      ; AND
(bvor x y)       ; OR
(bvxor x y)      ; XOR
(bvnot x)        ; NOT (complement)

; Shifts
(bvshl x y)      ; Left shift
(bvlshr x y)     ; Logical right shift
(bvashr x y)     ; Arithmetic right shift

; Arithmetic (with overflow semantics)
(bvadd x y)      ; Addition
(bvsub x y)      ; Subtraction
(bvmul x y)      ; Multiplication
(bvudiv x y)     ; Unsigned division
(bvsdiv x y)     ; Signed division

; Comparisons
(bvult x y)      ; Unsigned less than
(bvslt x y)      ; Signed less than

; Conversions
((_ int2bv 32) n)     ; Int to BitVec
(bv2int x)            ; BitVec to Int (unsigned)
```

### Proposed K Syntax

#### Option A: Dedicated BitVec Type with Standard Operators

```k
class BitwiseExample {
  // Bit vector declarations
  mask : BitVec[32] = 0xFF
  value : BitVec[32]
  
  // Standard operators work on BitVec
  result : BitVec[32] = mask & value    // bitwise AND
  combined : BitVec[32] = mask | value  // bitwise OR
  flipped : BitVec[32] = mask ^ value   // bitwise XOR
  inverted : BitVec[32] = ~mask         // bitwise NOT
  
  // Shifts
  shifted : BitVec[32] = value << 4     // left shift
  logical : BitVec[32] = value >> 4     // logical right shift
  arith : BitVec[32] = value >>> 4      // arithmetic right shift
  
  // Arithmetic has overflow semantics
  sum : BitVec[32] = mask + value       // wraps on overflow
  
  // Constraints
  req value > 0bv32                     // BitVec literal
  req result = 0x0F                     // Hex literal
}
```

#### Applying Bit Operators to Int

**Challenge**: In SMT-LIB2, `Int` is mathematical integers (arbitrary precision, no overflow). Bit operations only make sense on fixed-width representations.

**Z3's Approach**:
1. `Int` and `BitVec` are separate sorts
2. Conversion functions: `int2bv[N]` and `bv2int`
3. Cannot mix operations directly

**Proposed K Solution**: Operator overloading based on operand type

```k
class MixedOperations {
  // Int - mathematical integers, no bit operations
  bigNum : Int = 12345678901234567890
  
  // BitVec - fixed width, supports bit operations
  flags : BitVec[32] = 0xFF
  
  // Bit operators only valid on BitVec
  masked : BitVec[32] = flags & 0x0F      // OK
  // invalid : Int = bigNum & 0x0F        // ERROR: & not defined for Int
  
  // Explicit conversion when needed
  smallNum : Int = 100
  asVec : BitVec[32] = smallNum.toBitVec[32]  // Convert Int to BitVec
  backToInt : Int = flags.toInt               // Convert BitVec to Int
  
  // Arithmetic: + - * work on both, but different semantics
  intSum : Int = bigNum + 1              // No overflow
  vecSum : BitVec[32] = flags + 1bv32    // Wraps at 2^32
}
```

### Alternative: Int32/Int64 Types

For users who want familiar fixed-width integer semantics:

```k
class FixedWidthIntegers {
  // Fixed-width integer types (aliases for BitVec)
  byte : Int8 = 127
  short : Int16 = 32767  
  int : Int32 = 0x7FFFFFFF
  long : Int64 = 0x7FFFFFFFFFFFFFFF
  
  // Unsigned variants
  ubyte : UInt8 = 255
  uint : UInt32 = 0xFFFFFFFF
  
  // Bit operations work naturally
  masked : Int32 = int & 0xFF
  shifted : Int32 = int << 8
  
  // Overflow behavior
  overflow : Int8 = 127 + 1    // Wraps to -128
}
```

### Implementation Plan

1. **Phase 1: Core BitVec Type**
   - Add `BitVec[N]` to type system
   - Add SMT translation to `(_ BitVec N)`
   - Support hex/binary literals with bit width

2. **Phase 2: Bitwise Operators**
   - Add operators: `&`, `|`, `^`, `~`
   - Add shifts: `<<`, `>>`, `>>>`
   - Type check to require BitVec operands

3. **Phase 3: Conversions**
   - Add `.toBitVec[N]` for Int
   - Add `.toInt` for BitVec
   - Handle sign extension

4. **Phase 4: Convenience Types (Optional)**
   - Add Int8, Int16, Int32, Int64 as aliases
   - Add UInt8, UInt16, UInt32, UInt64

---

## Feature: Java Constructor Calls

### Current State

- Grammar supports: `Type(args)` via `CtorApplExp`
- ExternalFunctions has: `evaluateConstructor()`
- TypeChecker has partial support for detecting constructor calls
- **Limitation**: The TypeChecker doesn't handle all cases correctly, particularly:
  - Type comparison between external class types and property declaration types
  - Constructor argument type validation

### What Works Today

Static method factory calls work:
```k
class FactoryExample {
  value : Int
  req value = java.lang.Integer.valueOf(42)  // Works!
}
```

### What Needs Work

Direct constructor calls:
```k
class ConstructorExample {
  price : java.math.BigDecimal = java.math.BigDecimal("99.99")  // Type mismatch error
}
```

### Proposed Syntax

```k
import java.math.BigDecimal
import java.util.Date

class JavaConstructorTest {
  // Call Java constructor
  price : BigDecimal = BigDecimal("123.45")
  
  // Use in expressions
  total : BigDecimal = price.multiply(BigDecimal("1.1"))
  
  // Constraints (if object has comparable semantics)
  req price.compareTo(BigDecimal("100")) > 0
}
```

### Implementation Plan

1. Fix TypeChecker to properly handle `IdentType` for external Java classes
2. Add type comparison logic that recognizes external Java types
3. Validate constructor arguments using reflection
4. Add test cases for various Java classes

---

## Feature: IEEE Floating Point

### Why Not Just Real?

K's `Real` type maps to SMT rationals:
- Exact arithmetic (no rounding errors)
- No overflow, underflow, NaN, infinity
- Different from hardware floats

IEEE FP is needed for:
- Modeling actual floating-point hardware
- Verifying numerical algorithms
- Detecting overflow/underflow bugs

### Z3's FP Theory

```smt2
(declare-const x (_ FloatingPoint 8 24))  ; Float (8 exp, 24 sig)
(declare-const y (_ FloatingPoint 11 53)) ; Double

(fp.add RNE x y)  ; Add with rounding mode
(fp.isNaN x)      ; Check for NaN
(fp.isInfinite x) ; Check for infinity
```

### Proposed K Syntax (Future)

```k
class FloatExample {
  x : Float32    // IEEE single precision
  y : Float64    // IEEE double precision
  
  // Operations have rounding
  sum : Float32 = x + y
  
  // Special value checks
  req !x.isNaN
  req !sum.isInfinite
}
```

**Priority**: Medium - most users can use `Real` for now.

---

## Numeric Type Conversions and Casting

### Current K Type System for Numbers

K has these numeric types:
- `Int` - arbitrary precision mathematical integers
- `Real` - exact rational numbers (no floating-point errors)
- `BitVec[N]` - fixed-width N-bit vectors
- `Int8/16/32/64` - signed fixed-width integers (aliases for BitVec with signed semantics)
- `UInt8/16/32/64` - unsigned fixed-width integers
- `Float32/Float64` - IEEE 754 floating-point (planned)

### Comparison with Common Programming Languages

| Language | Int | Float | Implicit Widening | Implicit Narrowing |
|----------|-----|-------|-------------------|-------------------|
| **Java** | byte→short→int→long | float→double | Yes (widening) | No (requires cast) |
| **C#** | sbyte→short→int→long | float→double | Yes (widening) | No (requires cast) |
| **Python** | int (arbitrary) | float | Yes (int→float) | Explicit |
| **Scala** | Byte→Short→Int→Long | Float→Double | Yes (widening) | No |
| **Rust** | i8→i16→i32→i64 | f32→f64 | **No** | **No** (all explicit) |
| **K** | Int (arbitrary) | Real | **Partial** | **No** |

### Current K Implicit Conversions

The `TypeChecker.areTypesEqual` method with `compatibility=true` allows these implicit conversions:

```scala
// Currently allowed when checking type compatibility:
Int ↔ BitVec[N]     // Int literals can be used as BitVec
Int ↔ SignedIntType   // Int can be Int32, etc.
Int ↔ UnsignedIntType // Int can be UInt32, etc.  
Real ↔ FloatType      // Real can be Float32/64
BitVec[N] ↔ SignedIntType(N)   // Same width
BitVec[N] ↔ UnsignedIntType(N) // Same width
```

### The `as` Operator

K has a type cast operator:

```k
value as Type
```

**Current Implementation:**
- Grammar: `expression 'as' type` → `TypeCastExp`
- AST: Creates `TypeCastCheckExp(cast=true, exp, ty)`
- TypeChecker: Simply returns target type (no validation!)
- SMT Backend: **NOT IMPLEMENTED** - casts are ignored!

### Issues with Current System

1. **No SMT conversion for casts**: The `as` operator is parsed but doesn't generate SMT conversion functions like `int2bv`, `bv2int`, `to_fp`, etc.

2. **Inconsistent with programming languages**: Most languages have clear widening/narrowing rules. K's compatibility is symmetric (Int↔BitVec) which is unusual.

3. **No width checking on narrowing**: `x as Int8` when `x : Int` might overflow - this should be constrained.

4. **No Int↔Real conversion**: Unlike most languages, K doesn't implicitly convert `Int` to `Real` in mixed expressions.

### Proposed Improvements

#### Option A: Explicit-Only (Rust-like)
All conversions require explicit casts. Simple but verbose.

```k
value : Int32 = 42 as Int32        // Required
mixed : Real = (x as Real) + 1.5   // Required
```

#### Option B: Safe Widening (Java/Scala-like)
Allow implicit widening, require explicit narrowing.

**Safe widening (implicit):**
- `Int8 → Int16 → Int32 → Int64 → Int`
- `UInt8 → UInt16 → UInt32 → UInt64`
- `Float32 → Float64 → Real`
- `Int → Real` (integers can become rationals)

**Narrowing (explicit cast required):**
- `Int → Int32` (might overflow)
- `Real → Float64` (might lose precision)
- `Int64 → Int8` (truncation)

```k
x : Int8 = 100
y : Int32 = x         // OK: widening
z : Int8 = y as Int8  // Required: narrowing

a : Int = 42
b : Real = a          // OK: Int can become Real
c : Int = b as Int    // Required: truncation
```

#### Option C: Current K + Fixes
Keep current symmetric compatibility but fix the SMT backend:

```k
x : Int = 42
y : BitVec[32] = x    // OK (current behavior)

// When converting Int → BitVec[N], add SMT constraint:
// (assert (and (>= x 0) (< x (^ 2 N))))  ; for unsigned
// or use ((_ int2bv N) x)

z : Int = y as Int    // Should emit (bv2int y)
```

### Required SMT Conversions

For proper numeric conversion support, K2Z3 needs to implement:

| Conversion | SMT Function |
|-----------|--------------|
| Int → BitVec[N] | `((_ int2bv N) x)` |
| BitVec[N] → Int (unsigned) | `(bv2nat x)` |
| BitVec[N] → Int (signed) | `(bv2int x)` |
| Int → Real | `(to_real x)` |
| Real → Int | `(to_int x)` (floor) |
| Real → Float | `((_ to_fp E S) RNE x)` |
| Float → Real | `(fp.to_real x)` |
| BitVec → Float | `((_ to_fp E S) RNE x)` |
| Float → BitVec | `(fp.to_sbv N RNE x)` |

### Recommendation

**Adopt Option B (Safe Widening)** for these reasons:

1. **Familiar to most programmers** - matches Java, Scala, C#
2. **Catches errors** - narrowing requires explicit acknowledgment  
3. **Minimal verbosity** - safe operations "just work"
4. **SMT-compatible** - Z3 has all needed conversion functions

### Implementation Plan

1. **Phase 1: Fix `as` operator** 
   - Add `TypeCastCheckExp` handling to K2Z3
   - Emit appropriate SMT conversion functions
   - Add type compatibility validation for casts

2. **Phase 2: Define widening hierarchy**
   - Establish clear widening relationships in TypeChecker
   - Add `isWideningConversion(from, to)` function
   - Update `areTypesEqual` to use directional compatibility

3. **Phase 3: Add conversion methods**
   - `.toInt32()`, `.toInt64()`, etc. for explicit narrowing
   - `.toReal()`, `.toFloat64()` for floating conversions
   - These provide more clarity than `as` operator

---

