# DSN_Pass.k Timeout Analysis

## Problem
`DSN_Pass.k` times out after 30 seconds when solving with Z3.

## Investigation Results

### Test Setup
Created `DSN_Pass-diagnostic.k` with toggleable constraint groups to isolate the problem.

### Findings

1. **Main timeline constraint (`Nominal_Anomaly_Impact`) is NOT the problem:**
   - Single branch (Nominal): **0.8s** - UNSAT
   - All three OR branches: **0.6s** - UNSAT
   - The large disjunction with 3 major branches solves quickly

2. **Individual additional constraints are NOT the problem:**
   - `AutonomousManeauver`: **0.6s** - UNSAT
   - `AvailManeauver`: **0.6s** - UNSAT
   - `DopplerAfterApproachOTM`: **0.7s** - UNSAT
   - `ProvideCarrier`: **0.7s** - UNSAT
   - `CoverageOTMS`: **0.6s** - UNSAT

3. **The problem is the COMBINATION of all constraints:**
   - All constraints enabled: **30s timeout** ❌
   - The interaction between multiple constraint groups causes Z3 to struggle

### Root Cause Analysis

The issue appears to be:
1. **Multiple interacting disjunctions**: Each additional constraint has OR branches that interact with the main timeline constraint's OR branches
2. **Temporal constraint complexity**: 75+ temporal operator calls (during, meets, overlaps, starts, finishes) create complex relationships
3. **Search space explosion**: When all constraint groups are enabled, Z3 must explore many combinations of OR branches across different constraints

### Potential Solutions

1. **Incremental Solving with Push/Pop** (Recommended)
   - Use `IncrementalSession` from `SolverEnhancements.scala`
   - Add constraints incrementally and check satisfiability at each step
   - Can identify which constraint group causes the problem
   - See `src/k/frontend/SolverEnhancements.scala` for implementation

2. **Assumption-Based Solving**
   - Use Z3's assumption mechanism to guide the solver
   - Fix certain variables (e.g., `requirements.missedpass`, `requirements.tolerate`) to reduce search space
   - Test each scenario separately

3. **Constraint Simplification**
   - Break large constraints into smaller, independent constraints
   - Use intermediate variables to reduce complexity
   - Consider if all constraints are necessary for the use case

4. **Solver Configuration**
   - Try different Z3 tactics/strategies
   - Increase timeout
   - Use different solver (CVC5, which may handle disjunctions better)

5. **Problem Decomposition**
   - Solve sub-problems separately
   - Use results from simpler problems to constrain harder ones
   - Implement a hierarchical solving approach

### Diagnostic Tool

The `DSN_Pass-diagnostic.k` file can be used to:
- Test individual constraint groups
- Identify problematic combinations
- Verify fixes

**Usage:**
1. Edit the `ENABLE_*` flags at the top of the file
2. Run: `./export/k src/examples/DSN_Pass-diagnostic.k`
3. Compare solve times to identify slow combinations

### Alternative Solvers

#### CVC5
- **Status**: Currently running (started with 300s timeout)
- **Command**: `./export/k src/examples/DSN_Pass.k -cvc5 -timeout 300`
- **Initial observation**: CVC5 started processing the problem (99.5% CPU usage)
- **Note**: CVC5 may handle disjunctions differently than Z3, potentially avoiding the timeout

#### MiniZinc
- **Status**: Translation generates model but has syntax errors
- **Issue**: Temporal operators (`meets`, `during`, `starts`, `finishes`, `overlaps`) are translated as function calls (e.g., `Schedule_DSN_Pass_1_meets[obj](Schedule_SunPointed_1[obj])`)
- **Problem**: MiniZinc doesn't support function calls in constraints - they need to be expanded inline
- **Example**: `meets(e)` should expand to `t2 = e.t1`, but the translator doesn't do this
- **Root cause**: `K2MiniZinc.scala` translates method calls generically without expanding function definitions
- **Workaround needed**: The translator would need to:
  1. Look up function definitions from the model
  2. Expand temporal operators inline to their definitions
  3. Handle nested function calls recursively
- **Generated model**: 138 lines in `.tmp/model.mzn` (has syntax errors)

### Next Steps

1. **Wait for CVC5 results** - Check if CVC5 can solve the problem within the 300s timeout
   - Status: Running for 25+ minutes (99% CPU usage)
   - Timeout set to 300 seconds
2. **Fix MiniZinc translator** - Add support for expanding temporal operator functions inline
   - Status: In progress - predicate generation added but function body extraction needs work
   - Issue: Function bodies not being extracted correctly from `FunDecl.body`
   - Approach: Using `getFunDecls` to get only functions defined in current class
   - Need to handle PropertyDecl with assignment (e.g., `t2 = e.t1`)
3. **Implement incremental solving** - Use `IncrementalSession` to add constraints incrementally
   - Status: `IncrementalSession` class found in `SolverEnhancements.scala`
   - Has `push()`, `pop()`, `check()`, `assert()` methods
   - Need to integrate into main solving path or create diagnostic tool
4. **Test with assumptions** - Fix certain variables to reduce search space
5. **Consider problem decomposition** - Break into smaller sub-problems

