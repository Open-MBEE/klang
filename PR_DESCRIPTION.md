# Pull Request: Advanced Solver Features

## Summary

This PR adds significant solver enhancements to the K language, including:
- Z3 sequence theory support for `Seq[T]` types
- Soft constraint support via Z3 Optimize API
- Unified solving loop with CEGAR refinement
- External Java function support
- Time/Duration type support with ISO 8601 parsing
- Type inference for undeclared variables
- Fixed-width numeric types (Int8-64, UInt8-64) with bitvector operations
- BitVec type with bitwise operators
- IDE plugins for IntelliJ and VS Code
- Jupyter notebook kernel for interactive constraint programming

## Features

### 1. Sequence Support (`Seq[T]`)
- Native Z3 sequence theory for `Seq[Int]`, `Seq[Real]`, `Seq[String]`
- Support for `Seq[A]` where A is a class (sequences of object references)
- Operations: `size`, `at`, `head`, `tail`, `last`, `isEmpty`, `contains`, `concat`, etc.
- `collect(x -> x.prop).sum()` pattern for aggregation

### 2. Soft Constraints
- New syntax: `soft req expression`
- Uses Z3 Optimize solver to maximize satisfied soft constraints
- Hard constraints always enforced, soft constraints are preferences

### 3. Unified Solver Loop
- `-unified` flag enables iterative solving
- CEGAR refinement for external function calls
- Object bounds for dynamic instantiation
- Pause/resume/sample support for anytime solving

### 4. External Java Functions
- Direct calls: `java.lang.Math.sqrt(100.0)`
- Import support: `import java.lang.Math` then `Math.abs(x)`
- Compile-time evaluation for concrete arguments
- CEGAR loop for symbolic arguments

### 5. Time/Duration Support
- `Time` type with ISO 8601 date literals
- `Duration` type with ISO 8601 and HH:MM:SS formats
- Microsecond precision
- Arithmetic comparisons

### 6. Type Inference
- Automatic type inference from initialization expressions
- Undeclared variable type inference from constraints

### 7. Fixed-Width Numeric Types
- Signed types: `Int8`, `Int16`, `Int32`, `Int64` (Z3 bitvectors)
- Unsigned types: `UInt8`, `UInt16`, `UInt32`, `UInt64`
- Full arithmetic operations with overflow semantics
- Type conversions between bitvectors and Int/Real
- Width conversions between different bitvector sizes

### 8. BitVec Type
- `BitVec[N]` type for N-bit bitvectors
- Bitwise operators: `band`, `bor`, `bxor`, `shl`, `shr`, `sar`, `bnot`
- Native Z3 bitvector theory support

### 9. IDE Support
**IntelliJ Plugin (v0.5.0):**
- Syntax highlighting with semantic colors
- Code completion for keywords, types, symbols
- Live templates (snippets) for common patterns
- Code folding for classes, functions, block comments
- Breadcrumb navigation
- Error highlighting and quick fixes
- Run K files directly (Cmd+Shift+R)
- Solution visualization tool window

**VS Code Extension (v0.5.0):**
- Syntax highlighting (TextMate grammar)
- Code completion with context awareness
- Snippets matching IntelliJ templates
- Inlay hints for parameter names/types
- Real-time error diagnostics
- Run K files (Cmd+Alt+R)
- Solution visualization panel

### 10. Jupyter Kernel
- Interactive constraint programming in notebooks
- Incremental model building across cells
- Rich HTML output for solutions
- Magic commands: `%reset`, `%solve`, `%show`, `%smt`, `%stats`, `%verbose`, `%load`, `%save`, `%timeout`, `%help`
- Clean output by default (verbose mode optional)
- Code completion for K keywords

## Test Results

**~125 tests pass (100%)**

Test performance improvements:
- Batch mode now default (~6x faster than sequential)
- SLL prediction mode for ANTLR (~2x faster parsing)
- Total test suite runs in ~20 seconds

## New Test Files

- `seq1.k` - Sequence operations
- `object_bounds[0-3].k` - Object creation and sequences of objects
- `soft_constraint[1-5].k` - Soft constraint variations
- `external[1-7].k` - External Java function calls
- `unified_solver1.k` - Unified solver test
- `cegar1.k` - CEGAR refinement test
- `time_duration_demo.k` - Time and Duration types
- `duration_hms1.k`, `duration_microseconds1.k` - Duration formats
- `type_inference_*.k` - Type inference tests
- `bitvec_test.k` - BitVec operations
- `bitvector_width_conversions.k` - Bitvector width conversions
- `bitvector_real_conversions.k` - Bitvector to Real conversions
- `numeric_types_test.k` - Fixed-width numeric types
- `signed_cast_test.k`, `signed_minimal.k`, `unsigned_minimal.k` - Type conversions

## Breaking Changes

None. All existing tests pass.

## Documentation

- `docs/OPAQUE_FUNCTION_SUPPORT.md` - Design doc for opaque functions
- `docs/SOLVER_IMPLEMENTATION_SUMMARY.md` - Solver features summary
- `docs/IDE_DESIGN_VISION.md` - IDE design vision
- `docs/features/` - Feature-specific documentation
- `ide/README.md` - IDE plugin documentation
- `jupyter/README.md` - Jupyter kernel documentation
- Updated `docs/` with session notes and feature documentation

## Usage Examples

### Soft Constraints
```k
class Preference {
  x : Int
  y : Int
  req x >= 0 && x <= 100
  req y >= 0 && y <= 100
  soft req x + y = 50  -- preference, may be relaxed
  soft req x = y       -- another preference
}
```

### Sequences of Objects
```k
class A {
  x : Int
  req x > 0 && x < 3
}
a_arr : Seq[A]
req a_arr.size > 2 && a_arr.size < 10
req 10 < a_arr.collect(a -> a.x).sum()
```

### External Functions
```k
import java.lang.Math
x : Real
req x >= 0
y : Real = Math.sqrt(x)
req y > 5
```

### Fixed-Width Integers
```k
x : Int32
y : UInt8
req x = -100
req y = 255
z : Int = x as Int  -- signed conversion
w : Int = y as Int  -- unsigned conversion
```

### BitVec Operations
```k
a : BitVec[8]
b : BitVec[8]
req a = 0b11110000
req b = 0b00001111
c : BitVec[8] = a bor b
req c = 0b11111111
```

### Unified Solver
```bash
./export/k -unified src/tests/mytest.k
```

### Jupyter Notebook
```bash
cd jupyter && ./install.sh
jupyter notebook  # Select 'K' kernel
```
