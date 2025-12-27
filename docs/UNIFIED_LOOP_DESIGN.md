# Unified Loop Design

This document describes the target architecture for the K solver's unified solving loop.

## Current State

Currently, solver selection and solving strategies are mutually exclusive in Frontend.scala:

```
-dsn-pass     → DSNPassSolver (scenario + soft constraints)
-heapcegar    → UnifiedSolver.solveWithHeapCegar (CEGAR for dynamic heap)
-cvc5         → CVC5Solver (external solver)
-yices        → YicesSolver (external solver)
(default)     → K2Z3.solveSMT (direct Z3)
```

This means combinations like `-yices -heapcegar` are not possible.

## Target Architecture

The unified loop should separate two orthogonal concerns:

### 1. Solver Backend (which SMT solver to use)
- **Z3** (default) - via native API
- **CVC5** - via SMT-LIB2 subprocess
- **Yices** - via SMT-LIB2 subprocess  
- **MathSAT** - via SMT-LIB2 subprocess
- **BAE** - via SMT-LIB2 subprocess

### 2. Solving Strategy (how to approach the problem)
- **Direct** - submit entire problem at once
- **Incremental** - add constraint groups incrementally
- **Heap CEGAR** - start with small heap, increase on UNSAT
- **Scenario-based** - explore disjunctions via assumptions
- **Soft constraints** - use optimization for hard groups

## Auto-Detection Based on Problem Properties

The unified loop should analyze the parsed model to determine the optimal strategy:

### Property Detection

```scala
case class ProblemProperties(
  hasDynamicHeap: Boolean,      // Recursive types, collections, var declarations
  hasScenarios: Boolean,        // Top-level if-then-else with Boolean conditions
  constraintCount: Int,         // Number of constraints
  classCount: Int,              // Number of class declarations
  hasExternalFunctions: Boolean // Opaque/external function calls
)
```

### Strategy Selection Rules

| Property | Detected By | Strategy |
|----------|-------------|----------|
| Dynamic heap | Recursive class refs, `Set`, `List` types, or classes with no fixed instance count | Heap CEGAR |
| Top-level disjunctions | `if missedpass then ... else if tolerate then ... else ...` | Scenario analysis |
| Many constraints | > 50 constraints | Incremental addition |
| Hard constraint groups | Group causes UNSAT/TIMEOUT | Soft constraint fallback |

### Example Files

#### lisp.k - Dynamic Heap
```
class S_Exp { var text: String }
class ListExp extends S_Exp {
  var leftExp: S_Exp    // ← Recursive reference
  var rightExp: S_Exp   // ← Recursive reference
}
var myList: ListExp
```
**Detected**: `S_Exp`, `Atom`, `ListExp` are dynamic classes (recursive refs)
**Strategy**: Heap CEGAR - start with 1 object each, increase on UNSAT
**Result**: SAT in ~0.5s with heap bounds S_Exp=10, Atom=1, ListExp=4

#### DSN_Pass.k - Fixed Heap with Scenarios
```
class Schedule {
  DSN_Pass_1: DSN_Pass   // ← All objects pre-declared
  DSN_Pass_2: DSN_Pass
  ...
  requirements: Requirements
}
// Implicit scenarios from:
// requirements.missedpass = true/false
// requirements.tolerance = true/false
```
**Detected**: All objects declared (fixed heap), disjunctive structure
**Strategy**: Scenario analysis → Incremental → Soft fallback
**Result**: SAT in ~23s (Nominal scenario with soft "Other Constraints")

## Unified Loop Algorithm

```
function unifiedSolve(model, timeout):
  props = analyzeModel(model)
  solver = selectSolver(props, userPrefs)
  
  if props.hasDynamicHeap:
    bounds = initializeSmallBounds(props.dynamicClasses)
  else:
    bounds = fixedBounds(model)
  
  scenarios = if props.hasScenarios then extractScenarios(model) else [FullProblem]
  
  for scenario in scenarios:
    result = solveScenarioIncremental(solver, model, scenario, bounds, timeout)
    
    case result of:
      SAT(model) → 
        if verifyModel(model) then return SAT(model)
        else continue  // Try next scenario or refine
      
      UNSAT →
        if props.hasDynamicHeap and canIncreaseBounds(bounds):
          bounds = increaseBounds(bounds)
          retry current scenario
        else:
          continue to next scenario
      
      TIMEOUT →
        partialModel = extractPartialModel(solver)
        if partialModel.isUseful:
          result = trySoftConstraintFallback(solver, model, scenario)
          if result.isSAT then return result
        continue to next scenario
  
  return UNSAT  // All scenarios exhausted

function solveScenarioIncremental(solver, model, scenario, bounds, timeout):
  groups = groupConstraintsBySource(model)
  solver.reset()
  solver.addBaseConstraints(model, bounds, scenario.assumptions)
  
  for group in groups:
    solver.push()
    solver.addGroup(group)
    
    result = solver.checkWithTimeout(adaptiveTimeout)
    
    case result of:
      SAT → continue
      UNSAT → 
        if group.canBeSoft:
          solver.pop()
          solver.addGroupAsSoft(group)
        else:
          return UNSAT
      TIMEOUT →
        solver.pop()
        solver.addGroupAsSoft(group)
        continue
  
  return solver.checkFinal()
```

## Configuration

```scala
case class SolveConfig(
  // Solver backend
  solver: SolverBackend = Z3,
  
  // Timeout strategy
  initialTimeout: Duration = 500.millis,
  maxTimeout: Duration = 30.seconds,
  timeoutGrowthFactor: Double = 2.0,
  
  // Heap strategy (auto-detected if Auto)
  heapStrategy: HeapStrategy = Auto,  // Fixed | CEGAR | Soft | Auto
  initialHeapBounds: Map[String, Int] = Map.empty,
  maxHeapBounds: Map[String, Int] = Map.empty,
  
  // Incremental strategy (auto-detected if Auto)
  incrementalMode: IncrementalMode = Auto,  // None | Assumptions | PushPop | Scenarios
  
  // Fallback options
  useSoftConstraintFallback: Boolean = true,
  bestEffort: Boolean = false
)
```

## Implementation Plan

1. **Create `SolveConfig` case class** with all orthogonal options
2. **Create `ProblemAnalyzer`** to detect problem properties from parsed model
3. **Refactor `UnifiedSolver.solve()`** to be the single entry point
4. **Move solver selection inside** UnifiedSolver, not as top-level branch
5. **Implement auto-detection** for heap strategy and incremental mode
6. **Remove `@preferred_options`** once auto-detection works reliably

