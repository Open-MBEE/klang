# Incremental Solving Test Results

## Test Date
December 23, 2025

## Test File
`src/examples/DSN_Pass-diagnostic.k`

## Test Command
```bash
./export/k src/examples/DSN_Pass-diagnostic.k -incremental -timeout 5000
```

## Results

### Success
✅ Incremental diagnostic compiles and runs successfully
✅ Push/pop scoping works correctly
✅ Constraint groups are added incrementally
✅ Timing information is captured per group
✅ Most constraint chunks are satisfiable quickly (2-12ms)

### Issues Found
⚠️ **Last chunk times out**: Constraint Chunk 30 (2 assertions) times out after 5 seconds
⚠️ **Arbitrary chunking**: Current approach splits assertions into groups of 10, which doesn't identify which named constraint is problematic

## Observations

1. **Incremental solving works**: The infrastructure for adding constraints incrementally is functioning correctly.

2. **Cumulative complexity**: Even though individual chunks are fast, the cumulative effect of all constraints may cause the last check to timeout. This suggests the problem isn't with individual constraints but with the interaction of many constraints.

3. **Chunking strategy limitations**: The current chunking approach (groups of 10 assertions) doesn't map to the actual constraint groups in DSN_Pass.k:
   - `Nominal_Anomaly_Impact`
   - `AutonomousManeauver`
   - `AvailManeauver`
   - `DopplerAfterApproachOTM`
   - `ProvideCarrier`
   - `CoverageOTMS`

## Recommendations

### Short-term
1. ✅ **Working**: Basic incremental diagnostic infrastructure
2. ⏳ **Improve chunking**: Use constraint mapping (`UtilSMT.constraintMessageMap`) to group assertions by their named constraints
3. ⏳ **Better timeout handling**: Continue checking remaining groups even after a timeout to identify all problematic areas

### Long-term
1. **Integration with unified loop**: Use push/pop within `UnifiedSolver.unifiedLoop()` for constraint-by-constraint addition
2. **Progress sharing**: Share `bestSoFar` models between incremental diagnostic and unified loop
3. **Constraint prioritization**: Use diagnostic results to inform unified loop which constraints to prioritize/defer

## Next Steps

1. Improve constraint grouping to use actual constraint names from the model
2. Test with longer timeout to see if full solve completes
3. Integrate push/pop into unified loop for constraint-by-constraint solving
4. Test on original DSN_Pass.k (without diagnostic flags)

## Code Status

- ✅ `IncrementalDiagnostic.scala` - Working, needs better constraint grouping
- ✅ `Frontend.scala` - `-incremental` flag integrated
- ✅ Compilation - All errors fixed
- ✅ Runtime - Basic functionality working

