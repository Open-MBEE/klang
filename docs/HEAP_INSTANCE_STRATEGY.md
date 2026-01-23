# Heap Instance Allocation Strategy

## Overview

The heap instance allocation uses a three-phase approach to balance:
1. **Concise solutions** - returning exactly the objects needed, no extras
2. **Completeness** - allowing enough instances for recursion/collections

## IMPORTANT: Single Loop Design

**There should be ONE loop, not two nested loops.**

Currently there are two loops:
- **HeapCEGAR outer loop**: Controls actual heap size (regenerates SMT with new HeapLayout)
- **UnifiedSolver inner loop**: Has `objectBounds` that increase, but doesn't change actual heap

This is **incorrect**. The UnifiedSolver inner loop increases per-class bounds (S_Exp: 2→4→8...) but the actual heap instances don't change until HeapCEGAR regenerates. This causes 8 inner iterations of UNSAT before HeapCEGAR iteration 2 finally works.

### Intended Design

The `objectBounds` in UnifiedSolver were meant to:
1. Increase with the actual heap size (not independently)
2. Restrict **total instances across all classes**, not per-class limits
3. Be based on counts of `used` flags (how many objects are actually used in a solution)

### Current Problem

The inner loop's bound-increasing logic is disconnected from actual heap allocation. Consider disabling it until `used` flags are working properly.

## The Three Phases (A, B, C)

### Phase A: Exact Instance Computation (Iteration 1)

Uses `instancesByComputation()` in `HeapLayout` to determine exactly how many instances of each class are needed based on static type analysis.

- Traverses the model via DFS
- Counts property references to each class type
- Does NOT propagate to subclasses in this iteration
- **Goal**: Most problems solve here with the exact number needed

**Example (lisp.k with `"(A.B)"`):**
- 1 ListExp for `myList`
- 2 S_Exp for `leftExp` and `rightExp`  
- 1 Atom (from deferred subclass propagation calculation, but applies in Phase B)

Note: On iteration 1, Atom count comes from `instancesByComputation()` which gives 1 for the Atom class directly (since Atom is mentioned in the type constraints). The S_Exp count of 2 is NOT propagated to Atom on iteration 1.

If SAT → done. If UNSAT → proceed to Phase B.

### Phase B: Subclass Instance Propagation (Iteration 2)

When a field is typed as a parent class (e.g., `S_Exp`) but the solution requires a subclass instance (e.g., `Atom`), we need heap slots for those subclasses.

**How it works:**
1. For each parent class with N instances, give each subclass at least N instances
2. This applies on iteration 2 and beyond (deferred subclass strategy)
3. Use `used` flags to allow over-allocation without solution bloat

**Example (lisp.k with `"(A.B)"`):**

Iteration 1 (Phase A): S_Exp=2, ListExp=1, Atom=1 → likely UNSAT (need 2 Atoms)
Iteration 2 (Phase B): 
- S_Exp increases: 2 → 4 (doubling)
- Atom propagated from S_Exp: 1 → 4
- ListExp increases: 1 → 2

With 4 Atoms available, should find SAT.

If SAT → done. If UNSAT → proceed to Phase C.

### Phase C: Instance Doubling for Recursion (Iteration 3+)

When the structure has unknown size due to:
- Recursive class definitions (ListExp has S_Exp which can be ListExp)
- Variable-sized collections (Seq, Set, Array with unknown length)

**How it works:**
1. Double the instance count for each dynamic class
2. Propagate increased bounds to subclasses
3. Repeat until SAT or max bound reached

**Example (lisp.k with `"((A.B).(C.D))"`):**

Needs: 3 ListExp + 4 Atoms = 7 objects

Iteration 1: ListExp=1, Atom=1 → UNSAT
Iteration 2: ListExp=2, Atom=4 → UNSAT (need 3 ListExp)
Iteration 3: ListExp=4, Atom=8 → SAT

Note: Even if the solver times out on this harder constraint, that's acceptable. The goal is correctness, not performance.

## The `used` Flags Mechanism

Each heap entry gets a boolean flag: `used_<class>_<index>`

### Purpose

The `used` flags serve multiple critical purposes:

1. **Eliminate unnecessary objects** from the returned K model
2. **Allow over-allocation** without solution bloat  
3. **Reduce combinatorial explosion** from subclass options
4. **Ensure connectivity**: only reachable objects are marked used
5. **Enable "tightness" analysis**: determine which objects MUST be in the solution

### Key Invariant

**Objects that must be in the solution have `used = true`, and any of their member references are also `used`.**

This connectivity propagation ensures:
- Root objects (TopLevelDeclarations) are always used
- If object X is used and X.prop = Y, then Y is used
- The "used" property flows through the object graph from roots

### Constraints

1. **Conditional invariants**: `(=> used_X (invariant X))`
   - Only enforce constraints for actually-used instances
   - Unused slots don't contribute to UNSAT

2. **Monotonicity (within each class)**: `(=> used_<class>_i+1 used_<class>_i)`
   - If slot i+1 is used, slot i must be used
   - Reduces search space: just deciding *how many*, not *which ones*
   - Applied separately within each class's list of instances

3. **First instance required**: `(assert used_<class>_first)`
   - The first instance of each class MUST be used
   - Ensures at least one object per class has invariants enforced
   - Combined with monotonicity: instances 0 to N are used for some N >= 1

4. **Connectivity**: `(=> (and used_X (= (prop const-X) Y)) used_Y)`
   - If object X is used and its property points to slot Y, then Y is used
   - Root object (TopLevelDeclarations at slot 0) is always used
   - Propagates "used" through object graph from roots

5. **Global limit**: `sum(used flags) <= max(instancesByComputation, numClasses)`
   - Bounds total used across all classes
   - Must be at least numClasses since first instance of each is forced
   - Allows flexibility in which subclass fills a parent slot

### Tightness and Unrolling

The logic for "tightness" (are all heap slots of a class being used?) combined with knowing which objects must be `used` can help determine the number of instances during "unrolling" (growing instances between iterations).

- If `heap_full!<class>` is true, all slots for that class are used → may need more
- If slots are unused, doubling probably won't help
- This analysis can guide more intelligent bound increases

### Current Status

`useHeapUsedFlags` is now **enabled** (`true`) in `ASTOptions` as of January 2026.

**Previous Issues (Resolved):**
1. **flight.k returned SAT instead of UNSAT** - Fixed by adding first-instance constraint
2. **employee.k returned UNSAT instead of SAT** - Fixed by correcting global limit calculation

**Key Fixes Applied:**
1. **First-instance constraint**: Each class's first heap slot is forced to be used: `(assert used_<class>_first)`. This prevents the solver from setting all used=false and making constraints vacuously true.

2. **Global limit fix**: The global used count limit now uses `max(computedInstances, numClasses)` instead of just `computedInstances`. This ensures the limit is at least as large as the number of classes (since each class has its first instance forced).

**Test Results:** All 276 tests produce correct SAT/UNSAT outcomes. Baseline mismatches are expected since the generated SMT includes the new used flag constraints.

## Implementation Details

### Related Mechanisms: `alive`, `heap_full`, `used`, and `visited`

There are several related mechanisms for tracking object usage:

#### 1. `k!alive!ClassName!ref` (in UnifiedSolver.scala)

**Definition:** `(= k!alive!C!ref (deref-is-C ref))`

True if heap slot `ref` contains an object of type `C`. This checks if the slot is *occupied* by the correct type.

**Soft constraint:** `(assert-soft (not k!alive!...) :weight 1)` - prefers fewer alive objects.

#### 2. `k!heap_full!ClassName` (in UnifiedSolver.scala)

**Definition:** `(= k!heap_full!C (and k!alive!C!1 k!alive!C!2 ...))`

True when **ALL** slots for class `C` contain objects of that class. This indicates "tightness" - the heap is full for this class and may need more instances.

**Used for:** HeapCEGAR decides whether to increase instances based on this.

#### 3. `k!used!ref` (in AbstractSyntax.scala, currently disabled)

**Definition:** Boolean flag per heap entry indicating if the slot is *needed* in the solution.

**Constraints when enabled:**
- Conditional invariants: `(=> used_X constraint_X)`
- Monotonicity: `(=> used_i+1 used_i)` within each class
- Global limit: `sum(used flags) <= totalInstanceBound`

#### 4. `visited` pattern (in K2Z3.scala, post-hoc)

After solving, `printObjectValue` traverses from root objects (top-level variables) and builds a `visited` set. Objects not in `visited` are printed as "Extra objects."

**This is the key insight:** The `visited` pattern is the post-hoc version of what `used` flags should capture during solving. The reachability from roots determines what's actually needed.

### Relationship Between Mechanisms

| Mechanism | When Applied | What It Checks |
|-----------|--------------|----------------|
| `alive` | Solve-time | Slot has object of correct type |
| `heap_full` | Solve-time | All slots for class are occupied |
| `used` | Solve-time (disabled) | Slot is reachable/needed from roots |
| `visited` | Post-solve | Objects reachable from top-level vars |

**Implementation Plan:**
- `used` flags should implement the `visited` pattern at solve-time
- Connectivity constraint propagates `used` from roots through object references
- `alive` checks type correctness; `used` checks reachability
- `heap_full` (tightness) plus `used` counts can guide unrolling decisions

### What `dynamicClasses` Should NOT Do

The `dynamicClasses` set identifies classes with recursive properties or that appear in collections. However, it should NOT be used to determine the number of instances. Instance counts come from:
1. `instancesByComputation()` for Phase A
2. Subclass propagation for Phase B
3. Doubling/unrolling for Phase C

`dynamicClasses` may be used to identify WHICH classes are candidates for bound increases, but the actual counts come from the phases above.

### Key Files

- `AbstractSyntax.scala`: `HeapLayout`, `instancesByComputation()`, `propagateInstancesToSubclasses()`, `useHeapUsedFlags`
- `UnifiedSolver.scala`: `k!alive!`, `k!heap_full!`, heap minimization soft constraints
- `K2Z3.scala`: `visited` pattern in `printObjectValue`, "Extra objects" detection
- `ASTOptions`: `useHeapUsedFlags`, `deferSubclassInstances`, `cegarIteration`, `instanceMultiplier`

## Current Implementation Status

- **Phase A**: Working (`instancesByComputation()` in HeapLayout, hooked into UnifiedSolver)
- **Phase B**: Working (subclass propagation in HeapCEGAR)
- **Phase C**: Working (`instanceMultiplier` doubling via HeapCEGAR)
- **Inner loop bound-increasing**: Disabled (SMT wasn't actually changing)
- **`alive` flags**: Working
- **`heap_full` flags**: Working (tightness check)
- **`used` flags**: Implemented but **disabled** (broke UNSAT tests, needs investigation)
- **`visited` pattern**: Working (post-solve)

## Solver Notes

- **Z3**: Poor string reasoning performance
- **CVC5**: Better for string constraints (lisp.k uses `-heapcegar-cvc5`)

## Test Cases

- `lisp.k` with `"(A.B)"`: Simple case, Phase A or B should suffice
- `lisp.k` with `"((A.B).(C.D))"`: Harder case, may require Phase C or timeout (acceptable)
