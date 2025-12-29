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
| **Bit Vectors** | QF_BV | `Int8`, `Int16`, `Int32`, `Int64`, `UInt8`, `UInt16`, `UInt32`, `UInt64` | **NEW** - Full SMT conversion support |
| **Type Conversions** | - | `x as Type` | **NEW** - Bitvector↔Int↔Real conversions |

### ⚠️ Partially Supported Features

| Feature | Issue | Status |
|---------|-------|--------|
| Tuple Constructor | `Tuple(x,y)` syntax not type-checked | Needs implementation |
| Complex Set Operations | Set comparison (`>=`) not SMT-native | Workaround needed |

### ❌ Missing SMT-LIB2/Z3 Features

| Feature | SMT Theory | Priority | Complexity | Notes |
|---------|-----------|----------|------------|-------|
| **Arrays (direct)** | QF_A | Medium | Medium | Map/lookup table semantics |

### 🚧 Planned Features

| Feature | Priority | Complexity | Notes |
|---------|----------|------------|-------|
| **Preferred Solver Comment** | High | Low | `// @preferred_solver cvc5` comment to specify preferred solver per file |
| **BAE Solver Integration** | High | Medium | Add kservices BAE as a solver backend |
| **Solver Performance Annotations** | Medium | Low | Annotate test/example files with preferred solver when one significantly outperforms Z3 |

### ✅ Recently Completed (December 2025)

| Feature | Description | Commit |
|---------|-------------|--------|
| **IEEE Floating Point** | `FloatLiteral` with `f/F` and `d/D` suffixes, FP arithmetic/comparisons | feature/advanced-solver-features |
| **Implicit Widening** | `Int8 → Int16 → Int32 → Int64` automatic in type compatibility | feature/advanced-solver-features |
| **Narrowing Warnings** | TypeChecker warns when narrowing conversions may lose data | feature/advanced-solver-features |
| **Java Constructor Calls** | `new Type(args)` syntax with `CtorApplExp` SMT generation | feature/advanced-solver-features |

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

**Current Implementation (December 2025):**
- Grammar: `expression 'as' type` → `TypeCastExp`
- AST: Creates `TypeCastCheckExp(cast=true, exp, ty)`
- TypeChecker: Returns target type
- SMT Backend: ✅ **FULLY IMPLEMENTED** for numeric types

### Supported Type Conversions

| Source Type | Target Type | SMT Function | Notes |
|-------------|-------------|--------------|-------|
| Int → SignedIntType(N) | `((_ int2bv N) x)` | N = 8, 16, 32, 64 |
| Int → UnsignedIntType(N) | `((_ int2bv N) x)` | N = 8, 16, 32, 64 |
| SignedIntType(N) → Int | `(ite (bvslt x 0) (- (bv2int x) 2^N) (bv2int x))` | Signed interpretation |
| UnsignedIntType(N) → Int | `(bv2int x)` | Unsigned interpretation |
| Int → Real | `(to_real x)` | Exact |
| Real → Int | `(to_int x)` | Truncates toward zero |
| SignedIntType(N) → Real | `(ite sign-bit (to_real (- (bv2int (bvneg x)))) (to_real (bv2int x)))` | Signed value to Real |
| UnsignedIntType(N) → Real | `(to_real (bv2int x))` | Unsigned value to Real |
| Real → SignedIntType(N) | `((_ int2bv N) (to_int x))` | Truncates to bitvector |
| Real → UnsignedIntType(N) | `((_ int2bv N) (to_int x))` | Truncates to bitvector |
| Float → Real | `(fp.to_real x)` | IEEE FP to exact |
| Real → Float | `((_ to_fp E S) RNE x)` | Real to IEEE FP |

### Bitvector Width Conversions

| Conversion | SMT Function | Notes |
|------------|--------------|-------|
| Signed narrow → wide | `((_ sign_extend N) x)` | Preserves sign |
| Unsigned narrow → wide | `((_ zero_extend N) x)` | Zero pads |
| Wide → narrow | `((_ extract M 0) x)` | Keeps lower bits |
| Signed ↔ Unsigned (same width) | identity | Just reinterprets bits |

### Example Usage

```k
class TypeConversions {
  // Signed bitvector to Real
  x: Int8 = 0x80          // -128 in signed interpretation
  y: Real = x as Real     // y = -128.0 ✓

  // Unsigned bitvector to Real  
  a: UInt8 = 0x80         // 128 in unsigned interpretation
  b: Real = a as Real     // b = 128.0 ✓

  // Real to bitvector
  r: Real = 42.7
  i: Int8 = r as Int8     // i = 42 (truncated)

  // Width conversions
  small: Int8 = 0xFF
  large: Int16 = small as Int16  // Sign-extended: 0xFFFF (-1)
}
```

### Regression Tests

- [bitvector_width_conversions.k](../../src/tests/bitvector_width_conversions.k): Tests all width conversion scenarios
- [bitvector_real_conversions.k](../../src/tests/bitvector_real_conversions.k): Tests bitvector ↔ Real conversions

### Future Improvements

1. **Width checking on narrowing**: `x as Int8` when `x : Int` could optionally constrain values to fit in target type.

2. **Implicit widening support**: Could add automatic widening (Int8 → Int16 → Int32) without explicit casts.

---

## Feature: Preferred Solver Specification

### Motivation

K currently supports multiple solver backends:
- **Z3** (default) - General-purpose SMT solver
- **CVC5** - Better performance for string constraints
- **MiniZinc** - Constraint programming solver
- **BAE** (planned) - kservices BAE solver

Different problems perform better with different solvers. Users should be able to:
1. Specify a preferred solver at the file level
2. Have test/example files annotated when a non-Z3 solver significantly outperforms

### Research: How Other Communities Specify Solver Preferences

| Community | Format | Solver Specification | Notes |
|-----------|--------|---------------------|-------|
| **SMT-LIB2** | `;` comments | No standard; `(set-logic X)` specifies theory | Files are solver-agnostic by design |
| **SMT-COMP** | `;` comments | `; solver: <name>` (informal metadata) | Used in benchmark headers |
| **DIMACS CNF** | `c` lines | `c solver: kissat` or `c recommended-solver: cadical` | Informal convention |
| **MiniZinc** | `%` comments | `% @solver gecode` or separate `.mzc` config | IDE/command-line preferred |
| **PDDL** | `;` comments | `:requirements` section for features | No solver preference standard |
| **ASP** | `%` comments | `% @solver clingo` | Project-specific |

**Key Finding**: Most communities use **comment-based metadata** rather than language constructs. This keeps the file portable and doesn't affect semantics.

### Current State

Solver selection is command-line only:
```bash
./export/k -cvc5 file.k      # Use CVC5
./export/k -minizinc file.k  # Use MiniZinc
./export/k file.k            # Default: Z3
```

### Proposed Syntax: Comment-Based Metadata

Following the convention of SMT-COMP and DIMACS communities, use a special comment:

```k
// @preferred_solver cvc5
// @status: sat

class StringHeavyProblem {
  // CVC5 handles these string constraints much faster
  s1 : String
  s2 : String
  req s1.contains(s2)
  req s1.length > 100
}
```

**Format**: `// @preferred_solver <name>` or `-- @preferred_solver <name>` at the start of the file

**Comment Style**: Match the existing comment style in the file:
- If file already has comments, use the same style (`//` or `--`)
- If file predominantly uses one style, match it
- Default to `//` for new files

**Supported metadata keys**:
- `@preferred_solver z3|cvc5|minizinc` - Preferred solver
- `@status: sat|unsat|unknown` - Expected result (for testing)
- `@timeout: <ms>` - Suggested timeout

**Examples**:

```k
// @preferred_solver minizinc
// Scheduling problems are faster with constraint programming

class SchedulingProblem {
  tasks : Int[10]
  // ...
}
```

```k
-- @preferred_solver cvc5
-- String-heavy problems benefit from CVC5

class StringProblem {
  // ...
}
```

### Why Comments Over Annotations?

1. **Portability** - File remains valid K even if solver isn't available
2. **Community convention** - Follows SMT-COMP, DIMACS patterns
3. **Non-semantic** - Solver choice doesn't affect the constraint specification
4. **Easy tooling** - Simple to parse with grep/sed for benchmarking scripts

### Implementation Plan

1. **Phase 1: Parser Support**
   - Scan first N lines of file for `// @preferred_solver` pattern
   - Extract solver name in `Frontend.scala` before parsing
   - Store in options map

2. **Phase 2: Solver Dispatch**
   - Check comment metadata before command-line option
   - Command-line `-cvc5`/`-minizinc` overrides comment if specified

3. **Phase 3: Benchmark and Annotate Files**
   - Run `python3 benchmark-solvers.py` to compare solvers
   - Review `.tmp/solver_benchmark_report.md` for recommendations
   - Run `python3 benchmark-solvers.py --annotate` to add comments to files
   - Do NOT add `@preferred_solver bae` (BAE is for benchmarking only)

4. **Phase 4: BAE Integration**
   - Add `BAESolver.scala` to integrate kservices BAE
   - BAE path: `~/git/kservices`
   - Include BAE in benchmarks but don't use for `@preferred_solver`

### Solver Selection Priority

1. Command-line flag (highest priority) - explicit override
2. `// @solver:` comment in file
3. Default (Z3)

---

## Feature: BAE Solver Integration

### Overview

BAE (from kservices at `~/git/kservices`) provides an alternative solving approach that may be better suited for certain problem types, particularly those involving external API integration.

### Implementation Plan

1. **Create BAESolver.scala**
   - Interface similar to `CVC5Solver.scala` and `MiniZincSolver.scala`
   - Connect to kservices BAE backend

2. **Add command-line option**
   - `-bae` flag to use BAE solver

3. **Support `@solver("bae")` annotation**

### Integration Points

```scala
// BAESolver.scala
object BAESolver {
  def solve(model: String): BAEResult = {
    // Connect to kservices BAE
    // ...
  }
}
```

---

## Future Work Items (Investigation & Implementation Backlog)

This section tracks items that need investigation or implementation. Items marked with ❓ are questions to discuss.

### Instance Generation & Performance

- [ ] **Add `-addInstancePerClass` option** (default: `false`)
  - Currently, at least one "extra" object is generated per class even when explicit instances are declared
  - This slows processing; should be opt-in for cases that need it
  - Need to investigate how this interacts with CEGAR and other solving modes
  - Related: The extra `BB` instance (Ref 7) in `b2.k` seems unnecessary when `b1` and `b2` are already declared

- [ ] **Investigate `-instances` option**
  - Current status: Appears to be ignored (sets `ASTOptions.numberOfInstances` but may not have effect)
  - Check git history to find what changes removed/broke support for this option
  - Document what it was supposed to do vs what it does now

### Command-Line Options Audit

- [ ] **Audit all Frontend.scala options**
  - Review each option in `parseArgs` to determine if it's functional or ignored
  - Current options to audit:
    - `-instances` (possibly ignored)
    - `-timeout`
    - `-classpath`
    - `-tests`, `-test`, `-baseline`
    - `-v`, `-query`, `-stats`, `-dot`, `-latex`, `-scala`
    - `-json`, `-mmsJson`
    - `-tc`, `-postnobody`
    - `-unified`, `-heapcegar`, `-heapcegar-cvc5`, `-heapsoft`
    - `-cvc5`, `-minizinc`, `-mzn-solver`, `-emit-mzn`
    - `-batch`, `-timing`, `-debug`
    - `-ktc`, `-ktc-gen`, `-tc-strict`, `-tc-inferred`, `-tc-ambiguous`, `-tc-flexible`
  - Document purpose and status of each option

- [ ] **Add `-help` / `--help` option and usage message**
  - ❓ Is there a reason this wasn't already implemented?
  - Check if `export/k` or `run-tests.sh` provides any help/usage
  - If not, implement comprehensive help output in Frontend.scala
  - Include descriptions of all options with examples

### Documentation

- [ ] **Document all command-line options**
  - Search through `.md` documents for existing documentation
  - Search git history for option-related changes
  - Create or update documentation with:
    - Option name and syntax
    - Purpose and behavior
    - Whether it's currently functional
    - Examples of usage
    - Interactions with other options

### Git History Investigation

- [ ] **Find commits that changed `-instances` behavior**
  - When was it working? When did it stop?
  - What was the intended behavior?

- [ ] **Review history of option parsing in Frontend.scala**
  - Identify any other options that may have been disabled or broken

### Set Operations

- [ ] **Implement Set size/length**
  - Currently gives error: "Set cardinality (size/length) is not supported in SMT"
  - Z3's `(Set T)` is backed by `(Array T Bool)` which has no cardinality function
  - Options to implement:
    1. For finite/bounded heaps: enumerate over all possible refs and count membership
    2. Use Z3's cardinality extension if available in newer versions
    3. Track set membership explicitly with auxiliary integer counters
    4. Require finite enumeration with known bounds (e.g., `Set[1..10]`)
  - Related: Bank.k example uses `customers.length() >= accounts.length()` which triggered this

### SMT Output & Verbosity

- [ ] **Review SMT output behavior**
  - SMT translation (input to solver) is currently included in `export/k` output
  - SMT model output (from solver) is written to `.tmp/k_smt_model.log`
  - Used to be output to stdout as well - investigate if `-debug` controls this
  - ❓ Should SMT input/output be suppressed by default? (cleaner user experience)
  - ❓ Is writing to `.tmp/` directory presumptuous? (may not exist if run from arbitrary location)
  - Consider: write to current directory, or make output location configurable

- [ ] **Investigate `-debug` option behavior**
  - Does it control SMT output to stdout?
  - What else does it enable/disable?
  - Document all effects of `-debug` flag

- [ ] **Suppress exception stack traces by default**
  - When errors are handled with a clear error message (like type check errors, K2SMT errors), don't show the Java stack trace
  - Stack traces clutter output and are not useful for end users
  - Only show stack traces when `-debug` is enabled
  - Applies to: `TypeCheckException`, `K2SMTException`, `K2Z3Exception`
  - Current behavior: always shows stack trace even for expected/handled errors

### Option Syntax Consistency

- [ ] **Unify single-dash vs double-dash option handling**
  - `Frontend.scala` uses single-dash prefix (e.g., `-instances`, `-debug`)
  - `run-tests.sh` uses double-dash prefix (e.g., `--timeout`, `--filter`)
  - Both should be tolerant of either `-` or `--` prefix
  - Help/usage output should show consistent style
  - Decide on convention: prefer single-dash (Unix style) or double-dash (GNU style)?

### Test Baselines & Organization

- [ ] **Review baseline representation**
  - Current: all baselines in one `src/tests/baseline.json` file
  - Consider: each `.k` file gets its own `.baseline` or `.expected` file
  - Pros of per-file baselines:
    - Easier to review changes in PRs (diff per test)
    - Can add/remove tests without touching shared file
    - Better for version control (fewer merge conflicts)
    - Easier to see what a test expects at a glance
  - Cons:
    - More files to manage
    - Need to update tooling (`run-tests.sh`, `check-baseline-regressions.py`)
  - ❓ What format? JSON, plain text, YAML?

- [ ] **Add baselines for all K files**
  - `src/tests/` - core regression tests (some have baselines, need full coverage)
  - `src/examples/` - example K files (currently no baselines)
  - Audit which files are missing baselines
  - Generate initial baselines for all existing tests

- [ ] **Import tests from kservices repo**
  - The `kservices` repository contains additional K test cases
  - Review and copy over relevant tests
  - Ensure no duplication with existing tests
  - May need to update tests for any language changes since they were written
  - Track provenance (where tests came from)

- [ ] **Test organization review**
  - `src/tests/` vs `src/test/` - clarify purpose of each
  - `src/examples/` - should these also be tests? Currently not run by `run-tests.sh`
  - Consider unified test structure

### Z3 Native Library Management

- [ ] **Fix `select-z3-architecture.sh` reliability**
  - Script reports success but doesn't always actually copy libraries
  - Observed: `export/lib/libz3java.dylib` remained x86_64 after script claimed arm64 was configured
  - Manual `cp -f lib/arm64/libz3*.dylib export/lib/` was required to fix
  - Issues to investigate:
    - Does the script verify the copy succeeded?
    - Does it verify the architecture of the destination file after copy?
    - Are there permission issues preventing overwrites?
    - Is the copy source path correct? (script uses `export/lib/$LIB_DIR/` but arm64 libs are in `lib/arm64/`)
  - Suggested fixes:
    1. After copy, verify with `file` command that destination has correct architecture
    2. Add verbose output showing actual files being copied
    3. Fail if verification shows wrong architecture
    4. Consider using `cp -f` to force overwrite
  - Related: `UnsatisfiedLinkError: no libz3java in java.library.path` symptom

### Synthetic Function Call Instantiation ✅ IMPLEMENTED

- [x] **Add synthetic calls for uncalled functions**
  - Similar to how extra heap objects are created for uninstantiated classes
  - For each function that is not called anywhere in the model:
    - Create synthetic input variables (`_synth_f_x`, `_synth_f_y`, etc.)
    - Extract body constraints (`req` statements) and assert them with synthetic args
    - This ensures functions are "callable" and detects unsatisfiable preconditions
  - Example: `fun f(x: Bool): Int { req x && !x; ... }` with no calls returns UNSAT
  - Test case: `src/tests/unsat_function.k`
  - Implementation: `UtilSMT.generateSyntheticFunctionCalls()` in `AbstractSyntax.scala`

### Architecture: Solver-Agnostic Constraint Processing

- [ ] **Move synthetic instance generation from SMT to K level**
  - Currently: synthetic class instances and function calls are generated in SMT
  - Proposed: generate them at the K AST level before translation to any solver
  - Benefits:
    - MiniZinc and other backends would get the same capabilities
    - SMT-specific code would be isolated to the SMT wrapper
    - More maintainable and testable
  - Related code:
    - `K2Z3.modelToConstraints()` already converts Z3 models back to K constraints
    - This could be generalized for verification and seeding at K level
  - Components to refactor:
    1. Synthetic class instances → K AST modification
    2. Synthetic function calls → K AST modification
    3. Hard constraint verification → K constraint evaluation
    4. Soft constraint handling → K-level optimization hints

### Questions to Resolve (❓)

- ❓ Why was no `-help` option implemented? Was it intentional or oversight?
- ❓ What should `-instances` actually control? Per-class count? Global count?
- ❓ How should instance generation interact with CEGAR refinement?
- ❓ Should there be an annotation alternative to `-addInstancePerClass`? (e.g., `@extraInstances(false)`)
- ❓ Should SMT output be suppressed by default? What's the intended audience for verbose output?
- ❓ Where should SMT model output files be written? (`.tmp/`, current dir, configurable?)

---
