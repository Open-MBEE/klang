# Incremental Solving Integration with Unified Loop

## Overview

This document tracks the integration of incremental solving (push/pop strategy) with the unified solving loop for addressing hard problems like DSN_Pass.k.

## Current State

### UnifiedSolver (Unified Loop)
- **Location**: `src/k/frontend/UnifiedSolver.scala`
- **Features**:
  - CEGAR refinement for external functions
  - Heap-bound CEGAR (start small, increase on UNSAT)
  - Progress tracking (`bestSoFar`, `lastSample`)
  - Timeout handling with best-effort solutions
  - Iteration-based solving loop

### IncrementalDiagnostic (New)
- **Location**: `src/k/frontend/IncrementalDiagnostic.scala`
- **Features**:
  - Uses `IncrementalSession` with push/pop scopes
  - Adds constraints incrementally in groups
  - Identifies which constraint group causes timeout/UNSAT
  - Reports timing per group

## Integration Goals

### 1. Constraint-by-Constraint Addition
The unified loop currently regenerates the full SMT model each iteration. We want to:
- Use push/pop to add constraints incrementally
- Avoid full SMT regeneration when possible
- Track which constraints are added in each iteration

### 2. Progress Information
Both systems track progress, but differently:
- **UnifiedSolver**: Tracks `bestSoFar` model, iteration count
- **IncrementalDiagnostic**: Tracks per-constraint-group results, timing

**Integration**: Combine both approaches:
- Use incremental diagnostic to identify problematic constraints
- Feed this information back to unified loop
- Unified loop can prioritize or relax problematic constraints

### 3. Partial Solutions
Both support partial solutions:
- **UnifiedSolver**: Returns `bestSoFar` on timeout (if `bestEffort` mode)
- **IncrementalDiagnostic**: Can return partial results per group

**Integration**: 
- Use incremental diagnostic to get partial solution even if full solve times out
- Unified loop can seed next iteration with partial solution

## Proposed Integration Points

### Option A: Incremental Diagnostic as Pre-Phase
1. Run incremental diagnostic first to identify problematic constraints
2. Use results to inform unified loop strategy:
   - Start with easier constraints
   - Defer problematic constraints
   - Use soft constraints for optional parts

### Option B: Incremental Solving Within Unified Loop
1. Unified loop uses push/pop for each constraint group
2. Add constraints incrementally within each iteration
3. Track which groups cause problems
4. Adjust strategy based on results

### Option C: Hybrid Approach
1. Use incremental diagnostic for initial analysis
2. Unified loop uses push/pop for constraint groups
3. Share progress information between both

## DSN_Pass.k Specific

### Diagnostic Version
- **File**: `src/examples/DSN_Pass-diagnostic.k`
- **Features**: Flags to enable/disable constraint groups
- **Usage**: Manual testing of which constraints cause problems

### Constraint Groups Identified
1. `Nominal_Anomaly_Impact` - Main timeline constraint (3 branches)
2. `AutonomousManeauver` - Autonomous capability requirement
3. `AvailManeauver` - Maneuver availability requirement
4. `DopplerAfterApproachOTM` - Doppler data collection
5. `ProvideCarrier` - Carrier following aborted OTM
6. `CoverageOTMS` - Real-time ground coverage

### Testing Strategy
1. Run incremental diagnostic on DSN_Pass-diagnostic.k
2. Identify which constraint group(s) cause timeout
3. Use diagnostic version flags to test combinations
4. Feed results into unified loop for smarter solving

## Implementation Notes

### Push/Pop in Unified Loop
Currently, unified loop doesn't use push/pop. To integrate:

```scala
// In unifiedLoop:
val session = new IncrementalSession(ctx, config)

// Add base constraints
session.push("base")
addBaseConstraints(session)

// Add constraint groups incrementally
for (group <- constraintGroups) {
  session.push(group.name)
  addGroupConstraints(session, group)
  
  val result = session.check()
  // Track results, adjust strategy
}
```

### Progress Sharing
Both systems should share:
- `bestSoFar` model
- Constraint group results
- Timing information
- UNSAT cores

## Next Steps

1. ✅ Test incremental diagnostic on DSN_Pass-diagnostic.k
2. ⏳ Analyze results to identify problematic constraints
3. ⏳ Integrate push/pop into unified loop
4. ⏳ Share progress information between systems
5. ⏳ Test combined approach on DSN_Pass.k

## References

- `docs/UNIFIED_SOLVING_LOOP.md` - Unified loop design
- `docs/investigations/incremental-solving-setup.md` - Original setup notes
- `src/k/frontend/UnifiedSolver.scala` - Unified loop implementation
- `src/k/frontend/IncrementalDiagnostic.scala` - Incremental diagnostic
- `src/k/frontend/SolverEnhancements.scala` - IncrementalSession implementation

