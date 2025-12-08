# Pull Request: Advanced Solver Features

## Summary

This PR adds significant solver enhancements to the K language, including:
- Z3 sequence theory support for `Seq[T]` types
- Soft constraint support via Z3 Optimize API
- Unified solving loop with CEGAR refinement
- External Java function support
- Time/Duration type support with ISO 8601 parsing
- Type inference for undeclared variables

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

## Test Results

**112/112 tests pass (100%)**

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

## Breaking Changes

None. All existing tests pass.

## Documentation

- `docs/OPAQUE_FUNCTION_SUPPORT.md` - Design doc for opaque functions
- `docs/SOLVER_IMPLEMENTATION_SUMMARY.md` - Solver features summary
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

### Unified Solver
```bash
./export/k -unified src/tests/mytest.k
```

