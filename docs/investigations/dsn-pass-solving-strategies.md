# Solving Strategies for DSN_Pass.k

## Your Key Insight

**DSN_Pass.k has a high-level disjunct (OR) of possible scenarios, and only a solution to ONE scenario is needed.**

This is correct! The structure is:
- `Nominal_Anomaly_Impact` has 3 OR branches (scenarios)
- Other constraints also have OR branches based on `requirements.missedpass` and `requirements.tolerate`
- Only **one** scenario needs to be satisfied

## How Incremental Solving Helps

### Problem Without Incremental Solving
- Z3 must explore **729 combinations** (3^6 constraint groups with 3 branches each)
- Timeout with no information about which constraints/scenarios are problematic
- No partial solutions

### Solution with Incremental Solving
1. **Constraint Isolation**: Add constraints incrementally, identify which groups cause problems
2. **Early Detection**: Know within seconds that `Valid` constraint is problematic
3. **Partial Solutions**: Get solution satisfying `OTM_Pass_Timing` even if later constraints fail
4. **Debugging Info**: Timing and UNSAT cores per constraint group

### Current Results
- `OTM_Pass_Timing`: SAT (4ms) ✅
- `Valid`: TIMEOUT (first problematic constraint) ⏱️
- All other constraints: TIMEOUT (cumulative effect) ⏱️

## How Unified Solver Helps Similarly

### Current Unified Solver Features
1. **Iterative Refinement**: Adapts strategy based on results (CEGAR, heap bounds)
2. **Progress Tracking**: Tracks `bestSoFar`, iteration count
3. **Adaptive Strategy**: Adjusts based on results (relax, defer, prioritize)
4. **Best-Effort Solutions**: Returns partial solutions on timeout
5. **State Management**: Manages object bounds, refinements, soft constraints

### How It Could Help with Scenarios
1. **Scenario Iteration**: Try each scenario in unified loop
2. **Progress Per Scenario**: Track `bestSoFar` for each scenario
3. **Adaptive Scenario Selection**: Prioritize easy scenarios first
4. **Best-Effort Across Scenarios**: Return best solution found across all scenarios
5. **Scenario State Management**: Track which scenarios have been tried, their results

## How These Can Be Combined

### Three-Level Strategy

#### Level 1: Scenario Selection
```
Identify 3 scenarios:
1. Nominal (missedpass = false)
2. Anomalous Tolerable (missedpass = true && tolerate = true)
3. Anomalous Not Tolerable (missedpass = true && tolerate = false)
```

#### Level 2: Incremental Constraints Within Scenario
```
For each scenario:
  1. Add scenario assumption (fix missedpass/tolerate)
  2. Add base constraints incrementally
  3. Add constraint groups incrementally:
     - OTM_Pass_Timing
     - Valid
     - Nominal_Anomaly_Impact (relevant branch only)
     - AutonomousManeauver (relevant branch only)
     - etc.
  4. Check after each group
```

#### Level 3: Unified Loop Coordination
```
Unified Loop:
  For each scenario (in priority order):
    - Use incremental solving within scenario
    - Track progress (bestSoFar per scenario)
    - If SAT: Return solution
    - If UNSAT/TIMEOUT: Try next scenario
    - Adapt strategy based on results
```

### Expected Workflow

```
1. Scenario Identification (instant)
   → Identify 3 scenarios from model structure

2. Try Scenario 1: Nominal (5-10 seconds)
   → Add assumption: missedpass = false
   → Add constraints incrementally
   → If SAT: Return solution ✅
   → If UNSAT/TIMEOUT: Continue to Scenario 2

3. Try Scenario 2: Anomalous Tolerable (5-10 seconds)
   → Add assumptions: missedpass = true, tolerate = true
   → Add constraints incrementally
   → If SAT: Return solution ✅
   → If UNSAT/TIMEOUT: Continue to Scenario 3

4. Try Scenario 3: Anomalous Not Tolerable (5-10 seconds)
   → Add assumptions: missedpass = true, tolerate = false
   → Add constraints incrementally
   → If SAT: Return solution ✅
   → If UNSAT/TIMEOUT: Return best-effort or report all failed
```

### Benefits of Combination

1. **Dramatically Reduced Search Space**: 729 combinations → 3 sequential scenarios
2. **Early Termination**: Return as soon as one scenario works
3. **Better Debugging**: Know which scenario and which constraint group fails
4. **Partial Solutions**: Get solutions even if some scenarios fail
5. **Progress Tracking**: Track progress across scenarios and constraints

## Implementation Approach

### Option A: Use Z3 Assumptions (Recommended)
```scala
// For each scenario:
val assumptions = createScenarioAssumptions(scenario)
val result = solver.check(assumptions: _*)
```

**Benefits**:
- Doesn't modify solver state
- Fast scenario switching
- Z3 can use assumptions to guide search

### Option B: Modify SMT Per Scenario
```scala
// For each scenario:
val scenarioSMT = baseSMT + scenarioConstraints
val result = solve(scenarioSMT)
```

**Benefits**:
- Clear separation
- Can save/restore state
- Easy to parallelize

### Option C: Use Diagnostic Version Flags
```scala
// Use DSN_Pass-diagnostic.k with flags:
// ENABLE_NOMINAL_BRANCH = true/false
// ENABLE_ANOMALOUS_BRANCH = true/false
// ENABLE_IMPACT_BRANCH = true/false
```

**Benefits**:
- Already implemented
- Easy to test
- Can combine with incremental solving

## Expected Performance Improvement

### Current (Full Solve)
- **Time**: Timeout (unknown duration, likely hours)
- **Search Space**: 729 combinations
- **Information**: None

### With Scenario-Based Incremental Solving
- **Time**: 5-30 seconds (depending on which scenario works)
- **Search Space**: 3 scenarios × incremental constraints
- **Information**: Know which scenario works, which constraints are problematic

### With Unified Loop Integration
- **Time**: Similar, but with better progress tracking
- **Search Space**: Same, but with adaptive strategy
- **Information**: Best solution across all scenarios, progress per scenario

## Next Steps

1. ✅ **Constraint grouping by name** - Working
2. ⏳ **Scenario extraction** - Identify scenarios from model
3. ⏳ **Z3 assumptions** - Use assumptions to fix scenario variables
4. ⏳ **Scenario-based solving** - Solve each scenario separately
5. ⏳ **Unified loop integration** - Combine with unified solver
6. ⏳ **Test on DSN_Pass.k** - Verify performance improvement

## Key Takeaway

**The disjunctive structure is an opportunity, not a problem!**

By recognizing that only one scenario needs to be satisfied:
- We can reduce search space dramatically
- We can get solutions much faster
- We can better understand which scenarios are feasible
- We can provide better debugging information

This is exactly what incremental solving and scenario-based approaches are designed for!

