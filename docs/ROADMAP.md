# K Language Roadmap

## Vision: K Written in K

The long-term goal is for K to be **self-hosting** - the K language implementation should itself be written in K wherever possible. This provides:

1. **Dogfooding** - We use our own language, finding issues and improvements
2. **Simplicity** - K constraints are often clearer than Scala/Java code
3. **Flexibility** - K programs can use different solvers (Z3, CVC5, MiniZinc, etc.)
4. **Elegance** - A language that can describe its own semantics

## Completed

### Type Checking via K (2026-01-01)
- KTypeChecker generates a K program encoding type constraints
- Type variables are Int (type IDs), type rules are constraints
- Z3 solves the constraints; UNSAT means type error
- No longer depends on the Scala TypeChecker

## In Progress

### Remove Legacy TypeChecker
- Replace Scala TypeChecker with KTypeChecker as default
- Remove `-ktc` flag (K-based type checking becomes the only option)
- Simplify codebase by ~2300 lines

## Future Opportunities

### Test Infrastructure as K
Instead of 600+ lines of shell script parsing output:
```k
class TestCase {
  file : String
  expectedResult : Result  // SAT, UNSAT, ERROR
  actualResult : Result

  req expectedResult = actualResult
}
```

### Solver Selection as K
Express heuristics as constraints:
```k
class SolverChoice {
  hasQuantifiers : Bool
  hasDynamicHeap : Bool
  complexity : Complexity
  solver : Solver

  req hasQuantifiers => solver != Optimize
  req hasDynamicHeap => solver = HeapCEGAR
}
```

### Inheritance Resolution as K
Diamond inheritance and field shadowing as constraint satisfaction.

### Baseline Comparison as K
Model equivalence checking using K's equality constraints.

### Code Generation as K
Transform AST to target language (SMT-LIB, Scala, Java) via K programs.

## Principles

1. **Prefer K over Scala/Java** for logic that is naturally constraint-based
2. **Keep orchestration in Scala** for performance-critical loops and I/O
3. **Test K features using K** - meta-circular testing
4. **Document in K** - examples are executable specifications
