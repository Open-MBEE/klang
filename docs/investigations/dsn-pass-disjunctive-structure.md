# DSN_Pass.k Disjunctive Structure Analysis

## Your Observation is Correct!

Yes, you're absolutely right! DSN_Pass.k has a high-level disjunct (OR) of possible scenarios, and only a solution to **one** scenario is needed.

## Structure Analysis

### Main Constraint: `Nominal_Anomaly_Impact`
Has **3 OR branches** (scenarios):
1. **Nominal Approach**: `requirements.missedpass = false`
2. **Anomalous Approach Maneuver**: `requirements.missedpass = true && requirements.tolerate = true`
3. **Impact Possible**: `requirements.missedpass = true && requirements.tolerate = false`

### Other Constraints Also Have OR Branches

**`AutonomousManeauver`** has 3 branches:
- `requirements.missedpass = false`
- `requirements.missedpass = true && requirements.tolerate = true && ...`
- `requirements.missedpass = true && requirements.tolerate = false && ...`

**`AvailManeauver`** has 3 branches:
- `requirements.missedpass = false && OTM_Prime.success = true`
- `requirements.missedpass = true && requirements.tolerate = true && ...`
- `requirements.missedpass = true && OTM_Prime.success = false && ...`

**`DopplerAfterApproachOTM`** has 3 branches (same pattern)

**`ProvideCarrier`** has 3 branches (same pattern)

**`CoverageOTMS`** has 3 branches (same pattern)

## The Problem: Combinatorial Explosion

When Z3 sees all these OR expressions together, it must explore:
- **3 branches** from `Nominal_Anomaly_Impact`
- **3 branches** from `AutonomousManeauver`
- **3 branches** from `AvailManeauver`
- **3 branches** from `DopplerAfterApproachOTM`
- **3 branches** from `ProvideCarrier`
- **3 branches** from `CoverageOTMS`

**Total combinations to explore**: 3^6 = **729 possible combinations**!

But actually, many of these branches share the same condition (`requirements.missedpass` and `requirements.tolerate`), so the effective scenarios are:

1. **Nominal**: `missedpass = false`
2. **Anomalous Tolerable**: `missedpass = true && tolerate = true`
3. **Anomalous Not Tolerable**: `missedpass = true && tolerate = false`

## Strategy: Case Splitting

Since only **one** scenario needs to be satisfied, we can:

### Option 1: Solve Each Scenario Separately
```
For each scenario:
  1. Add assumption: requirements.missedpass = X && requirements.tolerate = Y
  2. Solve with all constraints
  3. If SAT, return solution
  4. If UNSAT, try next scenario
```

### Option 2: Use Z3 Assumptions
```
1. Add all constraints
2. Use assumptions to guide which branch:
   solver.check(assumption1, assumption2, ...)
3. If UNSAT, try different assumptions
```

### Option 3: Incremental with Scenario Selection
```
1. Identify scenario conditions (missedpass, tolerate)
2. Add base constraints
3. Add scenario assumption
4. Add constraints incrementally
5. If timeout/UNSAT, try next scenario
```

## Implementation Strategy

### Phase 1: Extract Scenarios
```scala
case class Scenario(
  name: String,
  assumptions: List[BoolExpr],  // e.g., missedpass = false
  constraints: List[ConstraintGroup]
)

val scenarios = List(
  Scenario("Nominal", 
    List(missedpass = false),
    List(...)
  ),
  Scenario("AnomalousTolerable",
    List(missedpass = true, tolerate = true),
    List(...)
  ),
  Scenario("AnomalousNotTolerable",
    List(missedpass = true, tolerate = false),
    List(...)
  )
)
```

### Phase 2: Solve Each Scenario
```scala
for (scenario <- scenarios) {
  session.push(scenario.name)
  
  // Add scenario assumptions
  scenario.assumptions.foreach(session.assert)
  
  // Add constraints incrementally
  for (group <- scenario.constraints) {
    session.push(group.name)
    group.assertions.foreach(session.assert)
    
    val result = session.check()
    if (result.isUnsat || result.isUnknown) {
      // This scenario doesn't work, try next
      session.pop() // pop group
      session.pop() // pop scenario
      break
    }
  }
  
  if (result.isSat) {
    return result  // Found solution!
  }
}
```

## Benefits

1. **Dramatically Reduced Search Space**: Instead of exploring 729 combinations, we explore 3 scenarios sequentially
2. **Early Termination**: As soon as one scenario works, we're done
3. **Better Debugging**: If all scenarios fail, we know which specific scenario is problematic
4. **Parallelization**: Can solve scenarios in parallel

## Expected Performance

### Current Approach
- **Full solve**: Timeout (exploring all combinations)
- **Search space**: 3^6 = 729 combinations

### Scenario-Based Approach
- **Scenario 1 (Nominal)**: ~5-10 seconds
- **Scenario 2 (Anomalous Tolerable)**: ~5-10 seconds  
- **Scenario 3 (Anomalous Not Tolerable)**: ~5-10 seconds
- **Total worst case**: ~30 seconds (but likely much faster if first scenario works)

## Integration with Incremental Solving

Combine both strategies:

1. **Scenario Selection**: Choose which scenario to try
2. **Incremental Constraints**: Add constraints within scenario incrementally
3. **Early Detection**: If scenario fails early, move to next scenario

This gives us:
- **Scenario-level** optimization (try easy scenarios first)
- **Constraint-level** optimization (add constraints incrementally within scenario)

