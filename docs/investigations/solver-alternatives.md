# Alternative Solvers for DSN_Pass.k Performance

## Problem Characteristics

DSN_Pass.k has challenging characteristics:
- **75+ temporal operator calls** (before, meets, overlaps, starts, during, finishes, equal)
- **Large disjunction** - many alternative constraint combinations
- **Real-valued time constraints** (t1, t2, duration)
- **Complex event scheduling** with multiple interdependent events

## Current Performance

- **Z3**: Times out after 30 seconds (default timeout)
- **CVC5**: Very slow (tested, but not completed)
- **MiniZinc + Gecode**: Times out after 60 seconds
- **MiniZinc + HiGHS**: Times out after 60 seconds
- **MiniZinc + COIN-BC**: Times out after 60 seconds
- **MiniZinc + SCIP**: Times out after 60 seconds
- **MiniZinc + OR-Tools CP-SAT**: Times out after 60 seconds (use `-mzn-solver cp-sat`)

## Alternative Solver Options

### 1. MiniZinc Backend Solvers

Your system has these MiniZinc solvers available:
- **Gecode 6.2.0** (current default) - Generic CP solver
- **COIN-BC** - Mixed Integer Programming
- **CPLEX** - Commercial MIP solver (if licensed)
- **Gurobi** - Commercial MIP solver (if licensed)
- **HiGHS** - Open-source MIP solver
- **SCIP** - Open-source MIP solver
- **Xpress** - Commercial MIP solver (if licensed)

**Recommendations:**
- **Try Chuffed** - Install via `brew install minizinc-chuffed` or download from MiniZinc website. Chuffed is often faster for scheduling problems.
- **Try OR-Tools CP-SAT** - Install via `brew install minizinc-or-tools`. Google's CP-SAT is excellent for scheduling and temporal constraints.
- **Try HiGHS** - Already available, good for MIP formulations: `-mzn-solver highs`

### 2. SMT Solvers

**Already tried:**
- Z3 (timeout)
- CVC5 (very slow)

**Other SMT options:**
- **Yices 2** - Often faster than Z3 for certain problem classes
- **MathSAT 5** - Good for real arithmetic
- **Boolector** - Fast for bit-vector problems (not applicable here)
- **STP** - Another option, but less mature

**Integration approach:**
- ✅ **Yices 2**: Integrated! Use `-yices` flag
- ✅ **MathSAT 5**: Integrated! Use `-mathsat` flag
- Both follow the same pattern as CVC5 integration

### 3. Specialized Temporal Constraint Solvers

**Temporal CSP Solvers:**
- **Temporal CSP** - Specialized for Allen's interval algebra (which your temporal operators resemble)
- **Choco** - Java CP solver with temporal constraints
- **Gecode with temporal constraints** - Already using Gecode, but might need different model formulation

### 4. Problem Reformulation Strategies

Instead of changing solvers, consider:

**A. Incremental Solving**
- Use Z3's push/pop to test constraint groups incrementally
- Already have `IncrementalSession` in `SolverEnhancements.scala`
- Could identify which constraint combinations are problematic

**B. Constraint Relaxation**
- Start with a simplified model (fewer events, fewer constraints)
- Gradually add complexity
- Use diagnostic version (`DSN_Pass-diagnostic.k`) to isolate issues

**C. Domain Reduction**
- Add tighter bounds on time variables
- Use known constraints to reduce search space
- Pre-compute some relationships

**D. Alternative Formulation**
- Convert large disjunction to multiple smaller problems
- Use optimization to find "best" solution rather than any solution
- Break into sub-problems and solve separately

## Recommended Next Steps

### Immediate (Easy to Try)

1. **Try HiGHS (already available):**
   ```bash
   ./export/k src/examples/DSN_Pass.k -minizinc -mzn-solver highs -mzn-timeout 60000
   ```

2. **Install and test Chuffed:**
   ```bash
   brew install minizinc-chuffed
   ./export/k src/examples/DSN_Pass.k -minizinc -mzn-solver chuffed -mzn-timeout 60000
   ```

3. **Install and test OR-Tools CP-SAT:**
   ```bash
   # Option 1: Via Homebrew (if available)
   brew install minizinc-or-tools
   
   # Option 2: Manual installation
   # Download from https://github.com/MiniZinc/minizinc-or-tools
   # Or install via MiniZinc package manager:
   minizinc --install or-tools
   
   ./export/k src/examples/DSN_Pass.k -minizinc -mzn-solver or-tools -mzn-timeout 60000
   ```

### Timeout Support

All MiniZinc solvers now support timeout via `-mzn-timeout <milliseconds>`:
```bash
# 30 second timeout
./export/k src/examples/DSN_Pass.k -minizinc -mzn-solver gecode -mzn-timeout 30000

# 60 second timeout  
./export/k src/examples/DSN_Pass.k -minizinc -mzn-solver highs -mzn-timeout 60000

# 5 minute timeout
./export/k src/examples/DSN_Pass.k -minizinc -mzn-solver or-tools -mzn-timeout 300000
```

### Medium Effort

4. **Add Yices 2 integration:**
   - Similar to CVC5 integration
   - Yices often faster for real arithmetic
   - Good API for Java/Scala

5. **Implement incremental solving diagnostic:**
   - Use `IncrementalSession` to test constraint groups
   - Identify which combinations cause slowdown
   - Document findings

### Longer Term

6. **Problem-specific optimizations:**
   - Analyze the constraint structure
   - Add domain-specific heuristics
   - Consider constraint reordering/grouping

7. **Hybrid approach:**
   - Use fast solver for initial filtering
   - Use precise solver for final validation
   - Or decompose into sub-problems

## Testing Strategy

For each solver, test with:
1. Full `DSN_Pass.k` (baseline)
2. `DSN_Pass-diagnostic.k` with different constraint groups enabled
3. Simplified versions (fewer events)

Measure:
- Time to first solution (or timeout)
- Memory usage
- Solution quality (if multiple solutions exist)

## Notes

- **Real arithmetic** is inherently harder than integer arithmetic
- **Large disjunctions** create exponential search spaces
- **Temporal constraints** with real values are particularly challenging
- Some solvers may need **problem reformulation** to perform well

The fact that all current solvers struggle suggests the problem may be inherently difficult, and reformulation or decomposition might be more effective than just trying different solvers.

