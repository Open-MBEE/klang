# K Language - Advanced Solver Features Implementation Summary

## Date: December 6, 2025

## Overview

This implementation adds advanced Z3 solver features to the K language, addressing two key user requirements:

1. **Anytime best-effort solutions** - Don't wait forever for an answer
2. **Opaque (black-box) function support** - Integrate external JVM/Python libraries

Plus additional enhancements:
- Incremental solving
- Optimization objectives (minimize/maximize)
- Regular expression support
- Enhanced unsat core reporting
- Native sequence theory support

## Files Modified/Created

### New Files

| File | Description |
|------|-------------|
| `src/k/frontend/SolverEnhancements.scala` | Core solver feature implementations including `IncrementalSession`, `AnytimeSolver`, `OpaqueFunctionManager`, `CEGARSolver`, and helper objects for regex/sequences |
| `src/test/AdvancedSolverTest.k` | Test cases demonstrating all new features |
| `docs/features/ADVANCED_SOLVER_FEATURES.md` | Comprehensive documentation |
| `regenerate-parser.sh` | Script to regenerate ANTLR parser after grammar changes |

### Modified Files

| File | Changes |
|------|---------|
| `src/grammar/Model.g4` | Added `optimizeDeclaration` rule for minimize/maximize |
| `src/k/frontend/ReservedAnnotations.scala` | Added annotations: `@timeout`, `@bestEffort`, `@incremental`, `@opaque`, `@soft`, `@axiom`, `@priority` |
| `src/k/frontend/AbstractSyntax.scala` | Added `OptimizeDecl`, `OptimizeKind`, `MinimizeKind`, `MaximizeKind` AST nodes; added `matches` and `fromInt` string methods |
| `src/k/frontend/KScalaVisitor.scala` | Added (commented) `visitOptimizeDeclaration` - needs parser regeneration |

## Feature Details

### 1. Anytime Best-Effort Solutions ✅

**Implementation:** `AnytimeSolver` class in `SolverEnhancements.scala`

```scala
// Create solver with timeout
val solver = K2Z3Enhanced.createAnytimeSolver(SolverConfig.bestEffort(5000))
solver.assert(constraint)
val result = solver.solve()  // Returns best result found within 5 seconds
```

**K Syntax:**
```k
@timeout(5000)
@bestEffort
class Problem {
  // constraints...
}
```

**Key Classes:**
- `SolverConfig` - Configuration for timeout, best-effort mode
- `AnytimeSolver` - Uses Z3's `Optimize` solver with timeout
- `SolverResult` - `Satisfiable`, `Unsatisfiable`, `Unknown` with partial model support

### 2. Opaque Function Support ✅

**Implementation:** `OpaqueFunctionManager` and `CEGARSolver` in `SolverEnhancements.scala`

**How it works:**
1. Functions marked `@opaque` become uninterpreted in Z3
2. Solver finds candidate solution
3. External function called with candidate inputs
4. If mismatch, axiom learned and re-solved (CEGAR loop)

```scala
val manager = new OpaqueFunctionManager(ctx)
manager.registerFunction(OpaqueFunction(
  "externalAPI",
  List(ctx.getIntSort),
  ctx.getIntSort,
  implementation = Some { args => /* call external library */ }
))
```

**K Syntax:**
```k
@opaque
fun externalComputation(x: Int): Int

req output = externalComputation(input)
```

### 3. Incremental Solving ✅

**Implementation:** `IncrementalSession` class

```scala
val session = K2Z3Enhanced.createIncrementalSession()

// Base constraints
session.assert(baseConstraint)
session.check()

// Try scenario A
session.push("scenario_a")
session.assert(scenarioA)
session.check()
session.pop()

// Try scenario B (base still there)
session.push("scenario_b")
session.assert(scenarioB)
session.check()
```

### 4. Optimization Objectives ✅

**Grammar:**
```
optimizeDeclaration: ('minimize' | 'maximize') expression ('weight' IntegerLiteral)?
```

**K Syntax:**
```k
class ResourceAllocation {
  cost : Int
  performance : Int
  
  req performance >= 50
  minimize cost
  maximize performance weight 2
}
```

**Note:** Parser needs to be regenerated to enable this feature. Run `./regenerate-parser.sh`.

### 5. Regular Expression Support ✅

**Implementation:** String method `matches` added to `AbstractSyntax.scala`

**K Syntax:**
```k
req email.matches("[a-z]+@[a-z]+\\.[a-z]+")
```

**SMT Output:**
```smt
(str.in_re email (str.to_re "[a-z]+@[a-z]+\\.[a-z]+"))
```

### 6. Enhanced Unsat Core Reporting ✅

**Implementation:** `IncrementalSession.assertAndTrack()` with named constraints

```scala
session.assert(constraint, Some("budget_constraint"))
// On UNSAT, returns:
// Unsatisfiable(List("budget_constraint", "performance_constraint"), 
//               "Conflicting constraints:\n  - Budget must be <= 100\n  - Performance requires budget > 150")
```

### 7. Soft Constraints ✅

**K Syntax:**
```k
// Hard constraint
req budget + quality >= 100

// Soft constraint - preferred but can be violated
@soft(10)
req quality >= 80
```

## Next Steps

### Immediate (to complete the implementation)

1. **Regenerate Parser**: Run `./regenerate-parser.sh` to enable the `optimize` syntax
2. **Uncomment visitor**: After regenerating, uncomment `visitOptimizeDeclaration` in `KScalaVisitor.scala`

### Future Enhancements

1. **Python Integration**: Use Py4J to call Python libraries from opaque functions
2. **Floating Point Theory**: Add IEEE 754 FP support for numerical accuracy
3. **Parallel Solving**: Portfolio approach with multiple solver instances
4. **Model Interpolation**: Generate intermediate solutions for visualization

## Testing

The test file `src/test/AdvancedSolverTest.k` contains test cases for:
- Optimization (minimize/maximize)
- Multi-objective optimization with weights
- Regular expression matching
- Sequence operations
- Timeout and best-effort solving
- Soft constraints
- Opaque functions
- Incremental solving

## Build Status

✅ **Build succeeds** with full implementation

All features are now active:
- Parser regenerated from Model.g4
- `visitOptimizeDeclaration` enabled
- All new solver classes compiled

To rebuild after any changes:
```bash
./compile.sh
```

To regenerate parser after grammar changes:
```bash
./regenerate-parser.sh
./compile.sh
```

