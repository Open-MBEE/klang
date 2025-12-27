# Incremental Solving Integration with Alternative Solvers

## Current State

The incremental solving improvements (`IncrementalSession`, `IncrementalDiagnostic`, `ScenarioTrackingDiagnostic`) are currently **Z3-specific** because they use Z3's native Java API (`Context`, `Solver`, `push()`, `pop()`).

## SMT-LIB2 Support for Incremental Solving

SMT-LIB2 standard supports incremental solving through:
- `(push)` - Push a new scope
- `(pop <n>)` - Pop n scopes
- `(check-sat)` - Check satisfiability in current scope

**All SMT solvers that support SMT-LIB2 should support these commands:**
- ✅ Z3 (native API + SMT-LIB2)
- ✅ CVC5 (SMT-LIB2)
- ✅ Yices 2 (SMT-LIB2)
- ✅ MathSAT 5 (SMT-LIB2)

## Potential Integration Approaches

### Option 1: SMT-LIB2-Based Incremental Diagnostics (Recommended)

Create a new diagnostic tool that generates SMT-LIB2 with `push`/`pop` commands and works with any SMT solver:

```scala
object SMTLibIncrementalDiagnostic {
  def diagnoseWithSolver(
    model: Model,
    smtModel: String,
    solver: String,  // "z3", "cvc5", "yices", "mathsat"
    timeoutMs: Long = 30000
  ): List[GroupResult] = {
    // 1. Parse SMT model to extract constraint groups
    // 2. Generate SMT-LIB2 with push/pop:
    //    (push)
    //    <base constraints>
    //    (check-sat)
    //    (push)
    //    <group 1 constraints>
    //    (check-sat)
    //    (pop 1)
    //    (push)
    //    <group 2 constraints>
    //    (check-sat)
    //    ...
    // 3. Call solver via SMT-LIB2 file
    // 4. Parse results
  }
}
```

**Benefits:**
- Works with all SMT solvers (Z3, CVC5, Yices, MathSAT)
- Uses standard SMT-LIB2 interface
- No solver-specific code needed

**Limitations:**
- Requires parsing SMT-LIB2 output (less structured than native API)
- May be slower than native API (file I/O overhead)

### Option 2: Solver-Specific Incremental APIs

Implement incremental solving using each solver's native API:

- **Z3**: Already implemented via `IncrementalSession`
- **CVC5**: Has Java API with incremental support
- **Yices**: Has C API (would need JNI wrapper)
- **MathSAT**: Has C API (would need JNI wrapper)

**Benefits:**
- Potentially faster (no file I/O)
- More control over solver state

**Limitations:**
- Requires significant implementation effort
- Need to maintain multiple solver-specific code paths
- Yices/MathSAT would need JNI bindings

### Option 3: Hybrid Approach

Use Z3's native API for diagnostics (current approach), but allow diagnostics to work with any solver by:
1. Running diagnostics with Z3 to identify problematic constraint groups
2. Then running the full problem with the selected solver

**Benefits:**
- Leverages existing Z3-based diagnostics
- Users can still use alternative solvers for final solving

**Limitations:**
- Diagnostics always use Z3 (may not reflect solver-specific behavior)

## Recommendation

**Short-term:** Keep current Z3-based diagnostics. They work well for identifying problematic constraint groups, and users can then run the full problem with their preferred solver.

**Long-term:** Implement Option 1 (SMT-LIB2-based incremental diagnostics) to enable solver-agnostic incremental diagnostics. This would allow users to:
- Run incremental diagnostics with CVC5, Yices, or MathSAT
- Compare how different solvers handle incremental constraint addition
- Identify solver-specific performance characteristics

## Implementation Notes

To implement SMT-LIB2-based incremental diagnostics:

1. **Constraint Grouping**: Reuse existing logic from `IncrementalDiagnostic.groupAssertionsByConstraint()`

2. **SMT-LIB2 Generation**: Generate SMT-LIB2 with structure:
   ```smt2
   (set-option :produce-models true)
   (set-option :produce-unsat-cores true)
   (declare-fun ...)  ; All declarations first
   (push)
   (assert ...)  ; Base constraints
   (check-sat)
   (get-model)  ; Optional: get model after base
   (push)
   (assert ...)  ; Group 1 constraints
   (check-sat)
   (get-model)  ; Optional: get model after group 1
   (pop 1)
   (push)
   (assert ...)  ; Group 2 constraints
   (check-sat)
   ...
   ```

3. **Solver Invocation**: Use existing solver wrappers (`CVC5Solver`, `YicesSolver`, `MathSATSolver`) but pass SMT-LIB2 file instead of single `check-sat`

4. **Result Parsing**: Parse solver output to extract results after each `check-sat`

## Testing

Test incremental diagnostics with:
- `DSN_Pass.k` - Known timeout problem
- Other complex examples with many constraint groups

Compare results across solvers to identify:
- Which solvers handle incremental addition better
- Solver-specific timeout behavior
- Performance differences

