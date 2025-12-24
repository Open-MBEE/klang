# Scenario-Based Strategy for DSN_Pass.k

## Your Insight is Correct!

Yes, you're absolutely right! DSN_Pass.k has a **high-level disjunct (OR) of possible scenarios**, and only a solution to **one** scenario is needed.

## Structure Analysis

### Main Disjunctive Constraint: `Nominal_Anomaly_Impact`
Has **3 OR branches** (scenarios):
1. **Nominal**: `requirements.missedpass = false`
2. **Anomalous Tolerable**: `requirements.missedpass = true && requirements.tolerate = true`
3. **Anomalous Not Tolerable**: `requirements.missedpass = true && requirements.tolerate = false`

### The Problem: Combinatorial Explosion

When Z3 sees all OR expressions together, it must explore:
- **3 branches** from `Nominal_Anomaly_Impact` ×
- **3 branches** from `AutonomousManeauver` ×
- **3 branches** from `AvailManeauver` ×
- **3 branches** from `DopplerAfterApproachOTM` ×
- **3 branches** from `ProvideCarrier` ×
- **3 branches** from `CoverageOTMS`

**Theoretical combinations**: 3^6 = **729 possible combinations**!

But actually, since all constraints share the same scenario variables (`missedpass` and `tolerate`), the effective scenarios are just **3**:
1. Nominal
2. Anomalous Tolerable  
3. Anomalous Not Tolerable

## Strategy: Solve Each Scenario Separately

### Approach 1: Use Z3 Assumptions (Recommended)

```scala
// For each scenario:
val assumptions = scenario.name match {
  case "Nominal" => 
    List(ctx.mkEq(missedpassVar, ctx.mkBool(false)))
  case "AnomalousTolerable" =>
    List(
      ctx.mkEq(missedpassVar, ctx.mkBool(true)),
      ctx.mkEq(tolerateVar, ctx.mkBool(true))
    )
  case "AnomalousNotTolerable" =>
    List(
      ctx.mkEq(missedpassVar, ctx.mkBool(true)),
      ctx.mkEq(tolerateVar, ctx.mkBool(false))
    )
}

// Solve with assumptions (doesn't modify solver state)
val result = solver.check(assumptions: _*)
```

**Benefits**:
- Doesn't modify solver state
- Can try multiple scenarios quickly
- Z3 can use assumptions to guide search

### Approach 2: Modify SMT for Each Scenario

```scala
// For each scenario, create modified SMT:
val scenarioSMT = baseSMT + scenarioAssumptions

// Solve modified SMT
val result = solve(scenarioSMT)
```

**Benefits**:
- Clear separation of scenarios
- Can save/restore solver state
- Easy to parallelize

### Approach 3: Use Diagnostic Version Flags

The diagnostic version (`DSN_Pass-diagnostic.k`) already has flags:
- `ENABLE_NOMINAL_BRANCH`
- `ENABLE_ANOMALOUS_BRANCH`
- `ENABLE_IMPACT_BRANCH`

**Benefits**:
- Already implemented
- Easy to test scenarios
- Can combine with incremental solving

## How Incremental Solving Helps with Scenarios

### Within Each Scenario:
1. **Add base constraints** (class invariants, types)
2. **Add scenario assumption** (fix missedpass/tolerate)
3. **Add constraint groups incrementally**:
   - `OTM_Pass_Timing`
   - `Valid`
   - `Nominal_Anomaly_Impact` (but only the relevant branch)
   - `AutonomousManeauver` (but only the relevant branch)
   - etc.
4. **Check after each group** to detect problems early

### Benefits:
- **Early detection**: Know within seconds if a scenario is UNSAT
- **Constraint prioritization**: Add easy constraints first
- **Partial solutions**: Get solution even if some constraints timeout

## How Unified Solver Helps with Scenarios

### Iterative Scenario Exploration:
```
Unified Loop:
  For each scenario:
    1. ENCODE: Generate SMT with scenario assumption
    2. SOLVE: Use incremental solving within scenario
    3. ANALYZE: 
       - If SAT: Done! Return solution
       - If UNSAT: Try next scenario
       - If TIMEOUT: Try next scenario (or increase timeout)
    4. REFINE: Adjust strategy based on results
```

### Benefits:
- **Progress tracking**: Track which scenarios have been tried
- **Best-effort**: Return best solution found across scenarios
- **Adaptive**: Adjust timeout/strategy per scenario

## Combined Strategy

### Phase 1: Scenario Identification
```
1. Extract scenario variables (missedpass, tolerate)
2. Identify 3 scenarios
3. Prioritize scenarios (e.g., try Nominal first)
```

### Phase 2: Scenario-Based Incremental Solving
```
For each scenario (in priority order):
  1. Add scenario assumption
  2. Add base constraints incrementally
  3. Add constraint groups incrementally
  4. Check after each group
  5. If SAT: Return solution
  6. If UNSAT/TIMEOUT: Try next scenario
```

### Phase 3: Unified Loop Integration
```
Unified Loop Iteration:
  For each scenario:
    - Use incremental solving within scenario
    - Track progress (bestSoFar per scenario)
    - Adapt strategy based on results
    - Return first SAT solution
```

## Expected Performance

### Current Approach
- **Full solve**: Timeout (exploring 729 combinations)
- **No information**: Don't know which scenario works

### Scenario-Based Approach
- **Scenario 1 (Nominal)**: ~5-10 seconds
- **Scenario 2 (Anomalous Tolerable)**: ~5-10 seconds
- **Scenario 3 (Anomalous Not Tolerable)**: ~5-10 seconds
- **Total worst case**: ~30 seconds
- **Best case**: ~5 seconds (if first scenario works)

### Combined with Incremental
- **Within each scenario**: Detect problems in seconds
- **Early termination**: Move to next scenario if current fails
- **Better debugging**: Know which constraint group fails in which scenario

## Implementation Plan

1. ✅ **Extract scenarios** from model structure
2. ⏳ **Use Z3 assumptions** to fix scenario variables
3. ⏳ **Solve each scenario separately** with incremental constraints
4. ⏳ **Integrate with unified loop** for progress tracking
5. ⏳ **Test on DSN_Pass.k** to verify performance improvement

## Key Insight

**The disjunctive structure is a feature, not a bug!**

By recognizing that only one scenario needs to be satisfied, we can:
- Reduce search space from 729 combinations to 3 sequential solves
- Get solutions much faster
- Better understand which scenarios are feasible

This is exactly the kind of problem structure that incremental solving and scenario-based approaches excel at!

