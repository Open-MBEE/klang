# Real vs Floating-Point in SMT-LIB2/Z3 and K

## Current K Implementation

K currently supports:
- **`Int`** - Mathematical integers (arbitrary precision)
- **`Real`** - Mathematical real numbers (arbitrary precision rationals)
- **`BitVec[N]`** - Fixed-width bit vectors (just implemented)

K does **NOT** currently support:
- **Floating-point numbers** (IEEE 754 floats/doubles)

## SMT-LIB2/Z3 Number Types

### 1. `Int` (Integers)
- **Theory**: `Ints` or `LIA` (Linear Integer Arithmetic)
- **Semantics**: Mathematical integers ℤ (infinite precision)
- **K mapping**: `IntType` → `ctx.getIntSort()`

### 2. `Real` (Reals)
- **Theory**: `Reals` or `LRA` (Linear Real Arithmetic)
- **Semantics**: Mathematical real numbers ℝ (arbitrary precision rationals)
- **NOT IEEE floating-point!**
- **K mapping**: `RealType` → `ctx.getRealSort()`
- **Key properties**:
  - Exact arithmetic (no rounding errors)
  - Division is exact: `(/ 1.0 3.0)` = exactly 1/3
  - No overflow, underflow, NaN, or infinity
  - Good for modeling mathematical relationships

### 3. `FloatingPoint` (IEEE 754)
- **Theory**: `FP` (Floating-Point)
- **Semantics**: IEEE 754 binary floating-point
- **Sizes**: 
  - `Float16` (half): 5 exponent, 10 significand bits
  - `Float32` (single): 8 exponent, 23 significand bits  
  - `Float64` (double): 11 exponent, 52 significand bits
  - `Float128` (quad): 15 exponent, 112 significand bits
- **Key properties**:
  - Finite precision (rounding errors)
  - Special values: +0, -0, +∞, -∞, NaN
  - Overflow and underflow
  - Multiple rounding modes
  - Models actual CPU floating-point behavior

## SMT-LIB2 Floating-Point Syntax

```smt2
; Declare floating-point constants
(declare-const x Float64)
(declare-const y (_ FloatingPoint 11 53))  ; 11 exp bits, 53 sig bits (= Float64)

; Floating-point literals
(fp #b0 #b10000000011 #b1000000000000000000000000000000000000000000000000000)  ; binary
(_ +zero 11 53)      ; positive zero
(_ -zero 11 53)      ; negative zero  
(_ +oo 11 53)        ; positive infinity
(_ -oo 11 53)        ; negative infinity
(_ NaN 11 53)        ; Not a Number

; Rounding modes
RNE  ; Round Nearest Ties to Even (default)
RNA  ; Round Nearest Ties to Away
RTP  ; Round Toward Positive
RTN  ; Round Toward Negative
RTZ  ; Round Toward Zero

; Operations (require rounding mode)
(fp.add RNE x y)     ; addition
(fp.sub RNE x y)     ; subtraction
(fp.mul RNE x y)     ; multiplication
(fp.div RNE x y)     ; division
(fp.sqrt RNE x)      ; square root
(fp.fma RNE x y z)   ; fused multiply-add

; Comparisons (no rounding mode needed)
(fp.eq x y)          ; equality (NaN != NaN)
(fp.lt x y)          ; less than
(fp.leq x y)         ; less than or equal
(fp.gt x y)          ; greater than
(fp.geq x y)         ; greater than or equal

; Predicates
(fp.isNaN x)         ; is NaN?
(fp.isInfinite x)    ; is +∞ or -∞?
(fp.isZero x)        ; is +0 or -0?
(fp.isNormal x)      ; is normalized?
(fp.isSubnormal x)   ; is denormalized?
(fp.isPositive x)    ; is positive?
(fp.isNegative x)    ; is negative?

; Conversions
(fp.to_real x)              ; FP to Real
((_ to_fp 11 53) RNE r)     ; Real to FP
((_ to_fp 11 53) RNE bv)    ; BitVec to FP (reinterpret)
(fp.to_sbv RNE 32 x)        ; FP to signed BitVec
(fp.to_ubv RNE 32 x)        ; FP to unsigned BitVec
```

## Z3 Java API for Floating-Point

```java
// Create floating-point sorts
FPSort float32 = ctx.mkFPSort32();
FPSort float64 = ctx.mkFPSort64();
FPSort custom = ctx.mkFPSort(11, 53);  // 11 exp, 53 sig

// Create constants
Expr<FPSort> x = ctx.mkConst("x", float64);
FPNum pi = ctx.mkFP(3.14159, float64);

// Create special values
FPExpr posZero = ctx.mkFPPosZero(float64);
FPExpr negZero = ctx.mkFPNegZero(float64);
FPExpr posInf = ctx.mkFPPosInf(float64);
FPExpr nan = ctx.mkFPNaN(float64);

// Rounding modes
FPRMExpr rne = ctx.mkFPRoundNearestTiesToEven();
FPRMExpr rtz = ctx.mkFPRoundTowardZero();

// Operations
FPExpr sum = ctx.mkFPAdd(rne, x, y);
FPExpr prod = ctx.mkFPMul(rne, x, y);
FPExpr sqrt = ctx.mkFPSqrt(rne, x);

// Comparisons
BoolExpr eq = ctx.mkFPEq(x, y);
BoolExpr lt = ctx.mkFPLt(x, y);

// Predicates
BoolExpr isNaN = ctx.mkFPIsNaN(x);
BoolExpr isInf = ctx.mkFPIsInfinite(x);
```

## Recommendations for K

### Option A: Add IEEE Floating-Point Types
```k
class Example {
  x : Float32       // or Float[32]
  y : Float64       // or Float[64]  
  z : Float[11,53]  // custom: 11 exp bits, 53 sig bits
  
  // Operations would require rounding mode
  req z = x +. y    // floating-point add (default RNE)
  req z = (x +. y) rounding RTZ  // explicit rounding
}
```

### Option B: Keep Real for Mathematical Modeling
If the goal is mathematical modeling rather than simulating CPU behavior, `Real` is often better:
- Simpler (no rounding modes, no special values)
- Faster solving in many cases
- Exact results

### Option C: Both
Support both `Real` (mathematical) and `Float` (IEEE 754) for different use cases.

## Use Cases

| Use Case | Recommended Type |
|----------|-----------------|
| Mathematical constraints | Real |
| Algorithm correctness proofs | Real |
| Numerical analysis | Float |
| Hardware verification | Float |
| Bug finding in C/Java float code | Float |
| Scientific computing models | Float |
| Financial calculations | Real (or fixed-point) |

## Current Status in K

- ✅ `Int` - Fully supported
- ✅ `Real` - Fully supported (mathematical reals)
- ✅ `BitVec[N]` - Just implemented
- ❌ `Float` - Not yet supported (would model IEEE 754)
- ❌ Arrays - Not yet supported in Z3 backend

## Implementation Complexity

| Feature | Complexity | Notes |
|---------|-----------|-------|
| Real | Low | Already done |
| Float literals | Medium | Need parser for float syntax |
| Float operations | Medium | Map to Z3 FP operations |
| Rounding modes | High | Need syntax for specifying modes |
| Float ↔ BitVec | Medium | Conversion functions |
| Float ↔ Real | Medium | Approximation functions |

