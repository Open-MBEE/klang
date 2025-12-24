# How Incremental Solving Helps with DSN_Pass.k

## Current Test Results

With improved constraint grouping, we can now see:
- **OTM_Pass_Timing**: SAT (4ms) ✅
- **Valid**: TIMEOUT (5s) ⏱️ - First problematic constraint
- **CoverageOTMS**: TIMEOUT (5s) ⏱️
- **ProvideCarrier**: TIMEOUT (5s) ⏱️
- **Nominal_Anomaly_Impact**: TIMEOUT (5s) ⏱️
- **AutonomousManeauver**: TIMEOUT (5s) ⏱️
- **DopplerAfterApproachOTM**: TIMEOUT (5s) ⏱️
- **AvailManeauver**: TIMEOUT (5s) ⏱️

## How Incremental Solving Helps

### 1. **Constraint Isolation**
**Problem**: When solving the full model, if it times out, you don't know which constraint(s) are causing the problem.

**Solution**: Incremental solving adds constraints one group at a time, checking satisfiability after each addition.

**Benefit**: 
- Identifies that `OTM_Pass_Timing` is satisfiable quickly
- Identifies that `Valid` is the first constraint to cause timeout when combined with base constraints
- Shows that all subsequent constraints also timeout (cumulative effect)

### 2. **Early Problem Detection**
**Problem**: You might spend hours waiting for a full solve that will never complete.

**Solution**: Incremental solving detects problems early - as soon as a constraint group causes timeout or UNSAT.

**Benefit**:
- Know within 5 seconds that `Valid` constraint is problematic
- Don't waste time trying to solve the full model
- Can focus debugging efforts on the problematic constraint

### 3. **Constraint Prioritization**
**Problem**: All constraints are treated equally, but some may be more important or easier to satisfy.

**Solution**: Incremental solving reveals which constraints are:
- Easy (satisfiable quickly)
- Hard (cause timeout)
- Conflicting (cause UNSAT)

**Benefit**:
- Can prioritize easy constraints first
- Can defer or relax hard constraints
- Can identify conflicting constraint combinations

### 4. **Partial Solutions**
**Problem**: If the full solve times out, you get nothing.

**Solution**: Incremental solving provides partial solutions after each successful constraint group addition.

**Benefit**:
- Get a solution that satisfies `OTM_Pass_Timing` even if later constraints fail
- Can use partial solutions to seed future solve attempts
- Can identify which parts of the model are satisfiable

### 5. **Debugging Information**
**Problem**: When a solve fails, you have limited information about why.

**Solution**: Incremental solving provides:
- Timing per constraint group
- UNSAT cores for conflicting constraints
- Clear identification of problematic constraint groups

**Benefit**:
- Know exactly which constraint causes problems
- Can focus debugging on specific constraint groups
- Can test constraint combinations systematically

## How Unified Solver Could Help Similarly

### 1. **Iterative Refinement**
**Current**: Unified solver already uses iterative refinement for:
- CEGAR (external function calls)
- Heap bounds (object creation)
- Soft constraints (optimization)

**Similar Benefit for Constraints**:
- Start with base constraints
- Add constraint groups incrementally
- Refine strategy based on results (relax, defer, or prioritize constraints)

### 2. **Progress Tracking**
**Current**: Unified solver tracks:
- `bestSoFar` model
- Iteration count
- Refinement history

**Similar Benefit for Constraints**:
- Track which constraint groups have been added
- Track partial solutions after each group
- Track timing/performance per group

### 3. **Adaptive Strategy**
**Current**: Unified solver adapts based on results:
- Increases heap bounds on UNSAT
- Adds CEGAR refinements on spurious solutions
- Relaxes soft constraints on UNSAT

**Similar Benefit for Constraints**:
- Defer problematic constraints to later iterations
- Make hard constraints soft (optional)
- Prioritize easy constraints first
- Split large constraint groups into smaller pieces

### 4. **Best-Effort Solutions**
**Current**: Unified solver returns `bestSoFar` on timeout if `bestEffort` mode is enabled.

**Similar Benefit for Constraints**:
- Return partial solution that satisfies as many constraint groups as possible
- Use soft constraints to maximize satisfied groups
- Provide solution even if some constraint groups timeout

### 5. **State Management**
**Current**: Unified solver manages:
- Object bounds state
- CEGAR refinement state
- Soft constraint weights

**Similar Benefit for Constraints**:
- Manage constraint group state (added, pending, deferred)
- Track constraint dependencies
- Manage push/pop scopes for constraint groups

## How These Can Be Combined

### Integration Strategy

#### Phase 1: Constraint Analysis (Pre-Solve)
```
1. Run incremental diagnostic to identify:
   - Easy constraints (fast SAT)
   - Hard constraints (timeout)
   - Conflicting constraints (UNSAT)
   
2. Build constraint strategy:
   - Priority order (easy first)
   - Soft vs hard classification
   - Dependency graph
```

#### Phase 2: Unified Loop with Incremental Constraints
```
Unified Loop Iteration:
  1. ENCODE: Generate SMT with current constraint groups
  2. SOLVE: Use push/pop to add constraints incrementally
     - Add base constraints
     - Add constraint groups in priority order
     - Check satisfiability after each group
     - Track partial solutions
  3. ANALYZE: Based on results
     - If SAT: Verify CEGAR, check heap bounds
     - If UNSAT: Identify conflicting constraint group
     - If TIMEOUT: Defer problematic constraints, return bestSoFar
  4. REFINE: Adjust strategy
     - Make hard constraints soft
     - Split large constraint groups
     - Increase timeout for specific groups
```

#### Phase 3: Shared State
```
Both systems share:
- bestSoFar: Partial solutions from incremental solving
- Constraint group results: Which groups are easy/hard
- Progress information: Iteration count, timing
- Strategy state: Priority order, soft/hard classification
```

### Example Combined Workflow

```
1. Incremental Diagnostic (5-10 seconds):
   - Identifies: OTM_Pass_Timing is easy, Valid is hard
   - Strategy: Prioritize OTM_Pass_Timing, defer Valid

2. Unified Loop Iteration 1:
   - Add base constraints + OTM_Pass_Timing
   - Result: SAT (quick)
   - bestSoFar: Solution satisfying base + OTM_Pass_Timing

3. Unified Loop Iteration 2:
   - Try adding Valid with increased timeout
   - Result: TIMEOUT
   - Strategy: Make Valid soft constraint, continue

4. Unified Loop Iteration 3:
   - Add remaining constraints (as soft)
   - Result: SAT with some soft constraints relaxed
   - Return: Best-effort solution
```

### Benefits of Combination

1. **Faster Problem Identification**: Incremental diagnostic quickly identifies problematic constraints
2. **Smarter Solving**: Unified loop uses diagnostic results to prioritize constraints
3. **Better Solutions**: Partial solutions from incremental solving seed unified loop
4. **Adaptive Strategy**: Unified loop adapts based on incremental results
5. **Progress Tracking**: Both systems share progress information

### Implementation Points

1. **IncrementalSession in Unified Loop**: Use push/pop within `UnifiedSolver.unifiedLoop()`
2. **Constraint Group Manager**: Track which groups have been added, their results
3. **Shared Progress State**: Both systems update shared `bestSoFar` and progress info
4. **Strategy Builder**: Convert incremental diagnostic results into unified loop strategy

## Expected Impact on DSN_Pass.k

### Current Situation
- Full solve: Times out (unknown duration)
- No information about which constraints are problematic

### With Incremental Solving
- Know within 5 seconds that `Valid` constraint is problematic
- Get partial solution satisfying `OTM_Pass_Timing`
- Can focus debugging on `Valid` constraint

### With Unified Solver Integration
- Use diagnostic results to prioritize constraints
- Make `Valid` soft constraint if it's optional
- Return best-effort solution even if some constraints timeout
- Adapt strategy based on constraint group results

### Combined Approach
- **5-10 seconds**: Identify problematic constraints
- **30-60 seconds**: Get best-effort solution satisfying most constraints
- **Ongoing**: Iteratively refine to satisfy more constraints

This is much better than waiting hours for a timeout with no information!

