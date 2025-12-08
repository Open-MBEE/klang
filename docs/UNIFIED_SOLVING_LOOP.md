# Unified Solving Loop Design

## Overview

This document describes the design for a unified solving loop that handles:
1. **CEGAR refinement** for external/opaque function calls
2. **Object creation bounds** for dynamic object instantiation
3. **Incremental/anytime solving** with pause, resume, and sampling
4. **Optimization** with soft constraints (max-SAT style)

## The Problem

K models can have several characteristics that require iterative solving:

### 1. External Functions (CEGAR)
```k
req y = java.lang.Math.sqrt(x)  -- Z3 doesn't know sqrt's semantics
```
Solution: Use uninterpreted function, verify after solving, refine if mismatch.

### 2. Dynamic Object Creation
```k
class Item { value : Int }
items : Seq[Item]  -- How many Items? Unknown!
req forall i : items . i.value > 0
```
Challenge: SMT requires all variables declared upfront. Can't dynamically add objects.

### 3. Anytime/Best-Effort Solutions
```k
@timeout(5000)
@bestEffort
class HardProblem { ... }
```
Need: Return partial solution if timeout, allow sampling during solve.

### 4. Optimization with Soft Constraints
```k
minimize cost
req cost >= 0        -- hard constraint
soft req cost < 100  -- soft constraint (preference)
```
Need: Max-SAT or weighted soft constraints.

## Unified Loop Design

```
┌─────────────────────────────────────────────────────────────────┐
│                    ITERATIVE SOLVER                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Input: K Model (parsed, type-checked)                          │
│  Output: Solution (possibly partial) or UNSAT                   │
│                                                                 │
│  State:                                                         │
│    - objectBounds: Map[ClassName → Int]  // max instances       │
│    - refinements: List[Constraint]       // CEGAR constraints   │
│    - softWeights: Map[ConstraintId → Double]                    │
│    - bestSoFar: Option[Model]                                   │
│    - checkpoints: Stack[(SMT, Model)]                           │
│                                                                 │
│  Algorithm:                                                     │
│  ──────────                                                     │
│  initialize objectBounds from model (minimum required)          │
│  while (!done && !interrupted && iteration < maxIterations) {   │
│                                                                 │
│    // Phase 1: ENCODE                                           │
│    smt = generateSMT(model, objectBounds, refinements)          │
│    addSoftConstraints(smt, softWeights)                         │
│                                                                 │
│    // Phase 2: SOLVE (with incremental mode)                    │
│    solver.push()  // checkpoint                                 │
│    result = solver.checkWithTimeout(timeoutMs)                  │
│                                                                 │
│    // Check for pause/sample requests                           │
│    if (pauseRequested) {                                        │
│      saveCheckpoint()                                           │
│      waitForResume()                                            │
│    }                                                            │
│    if (sampleRequested) {                                       │
│      emitSample(bestSoFar)                                      │
│    }                                                            │
│                                                                 │
│    // Phase 3: ANALYZE                                          │
│    result match {                                               │
│      case SAT(model) =>                                         │
│        bestSoFar = Some(model)                                  │
│                                                                 │
│        // CEGAR: verify external calls                          │
│        mismatches = verifyExternalCalls(model)                  │
│        if (mismatches.nonEmpty) {                               │
│          refinements ++= generateRefinements(mismatches)        │
│          continue  // re-solve with new constraints             │
│        }                                                        │
│                                                                 │
│        // Check if we need more objects                         │
│        if (needMoreObjects(model)) {                            │
│          increaseObjectBounds()                                 │
│          continue  // re-solve with more objects                │
│        }                                                        │
│                                                                 │
│        // All verified! Return solution                         │
│        done = true                                              │
│        return model                                             │
│                                                                 │
│      case UNSAT =>                                              │
│        // Maybe need more objects?                              │
│        if (canIncreaseObjectBounds()) {                         │
│          increaseObjectBounds()                                 │
│          continue                                               │
│        }                                                        │
│        // Or relax soft constraints?                            │
│        if (canRelaxSoftConstraints()) {                         │
│          relaxSoftConstraints()                                 │
│          continue                                               │
│        }                                                        │
│        // Truly unsatisfiable                                   │
│        return UNSAT                                             │
│                                                                 │
│      case TIMEOUT | UNKNOWN =>                                  │
│        if (bestEffortMode && bestSoFar.isDefined) {             │
│          return bestSoFar.get  // partial solution              │
│        }                                                        │
│        // Try with relaxed constraints or more time             │
│    }                                                            │
│                                                                 │
│    solver.pop()  // restore checkpoint                          │
│    iteration++                                                  │
│  }                                                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Object Bounds Strategy

### Initial Bounds
- Parse model to find minimum required instances
- `a : A` → at least 1 instance of A
- `items : Seq[Item]` → start with 0, may need more

### Encoding with Existence Variables
```smt2
; For class Item with max 5 potential instances:
(declare-const Item_0_exists Bool)
(declare-const Item_0_value Int)
(declare-const Item_1_exists Bool)
(declare-const Item_1_value Int)
; ...

; Items that don't exist have no constraints
(assert (=> (not Item_0_exists) true))  ; or default values

; Count existing items
(define-fun item_count () Int
  (+ (ite Item_0_exists 1 0)
     (ite Item_1_exists 1 0)
     ; ...
  ))
```

### When to Increase Bounds
1. **UNSAT with unsatisfied existence** - constraints require objects that don't exist
2. **Explicit constraint** - `req items.length > currentBound`
3. **Heuristic** - certain patterns suggest more objects needed

### Bound Increase Strategy
- Start small (0 or 1)
- Double on each UNSAT: 1 → 2 → 4 → 8
- Or linear: 1 → 2 → 3 → 4
- Cap at configured maximum

## Incremental Solving with Z3

Z3 supports incremental solving via:

### Push/Pop (Checkpoints)
```scala
solver.push()  // save state
solver.add(newConstraint)
solver.check()
solver.pop()   // restore state
```

### Assumptions
```scala
// Temporary assumptions without modifying solver state
solver.check(assumption1, assumption2, ...)
```

### Use Cases
1. **CEGAR**: Add refinement, check, pop if need different refinement
2. **Object bounds**: Push, try with N objects, pop, try with N+1
3. **Soft constraints**: Assumptions for optional constraints

## Soft Constraints / Max-SAT

For optimization and best-effort:

### Z3 Optimize API
```scala
val opt = ctx.mkOptimize()
opt.Add(hardConstraint)
opt.AssertSoft(softConstraint, weight, "group")
opt.Minimize(costExpr)
opt.Check()
```

### Encoding Soft Constraints in K
```k
soft req cost < 100  -- parsed as soft constraint
```

Or with weights:
```k
@soft(weight=10)
req cost < 100
```

### Max-SAT Style
- Each soft constraint gets a Boolean "relaxation" variable
- Minimize sum of relaxation variables that are true
- Produces solution satisfying maximum soft constraints

## Integration Points

### With Existing CEGAR
The current CEGAR implementation becomes one phase of the unified loop.
`solveSMTWithCEGAR` → `unifiedSolve`

### With Pause/Resume
Checkpoints saved via `push`, restored via `pop`.
Pause stores: current SMT, object bounds, refinements, best solution.

### With Sampling
During solve, periodically extract partial model.
Z3's `solver.getModel()` may work even mid-solve (implementation dependent).

### With @timeout/@bestEffort
Already implemented - integrate with loop's TIMEOUT handling.

## Implementation Plan

### Phase 1: Refactor Current CEGAR
- Extract loop into `UnifiedSolver` class
- Make CEGAR verification a pluggable phase

### Phase 2: Add Object Bounds
- Modify SMT generation to use existence variables
- Add bound tracking and increase logic
- Handle UNSAT → increase bounds

### Phase 3: Incremental Solving
- Use Z3 push/pop for checkpoints
- Proper pause/resume with state preservation

### Phase 4: Soft Constraints
- Parse soft constraint syntax
- Use Z3 Optimize API
- Max-SAT style relaxation

## Open Questions

1. **How to detect "need more objects"?**
   - UNSAT core analysis?
   - Specific constraint patterns?
   - User hints?

2. **Object bound upper limit?**
   - User-configured max?
   - Memory-based limit?
   - Timeout-based adaptive?

3. **Interaction between concerns?**
   - CEGAR + object bounds: which to try first?
   - Soft constraints + object bounds: how to prioritize?

4. **Performance implications?**
   - Regenerating SMT vs. incremental adds
   - When to reset vs. continue

## References

- Z3 Optimization: https://rise4fun.com/z3opt/tutorial
- Bounded Model Checking: Clarke et al.
- Max-SAT: Fu & Malik, "On Solving the Partial MAX-SAT Problem"
- BAE Implementation: ~/git/bae (arc consistency + local search)

