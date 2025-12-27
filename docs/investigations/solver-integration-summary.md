# Solver Integration Summary

## Completed Integrations

### MiniZinc Solvers (with timeout support)

All MiniZinc solvers now support `-mzn-timeout <milliseconds>`:

1. **Gecode** (default)
   ```bash
   ./export/k file.k -minizinc -mzn-solver gecode -mzn-timeout 60000
   ```

2. **OR-Tools CP-SAT** ✅ (installed via Homebrew)
   ```bash
   ./export/k file.k -minizinc -mzn-solver cp-sat -mzn-timeout 60000
   ```

3. **HiGHS** (MIP solver)
   ```bash
   ./export/k file.k -minizinc -mzn-solver highs -mzn-timeout 60000
   ```

4. **COIN-BC** (MIP solver)
   ```bash
   ./export/k file.k -minizinc -mzn-solver coin-bc -mzn-timeout 60000
   ```

5. **SCIP** (MIP solver, if available)
   ```bash
   ./export/k file.k -minizinc -mzn-solver scip -mzn-timeout 60000
   ```

### SMT Solvers

1. **Z3** (default)
   ```bash
   ./export/k file.k -timeout 60000
   ```

2. **CVC5** ✅ (already integrated)
   ```bash
   ./export/k file.k -cvc5 -timeout 60000
   ```

3. **Yices 2** ✅ (newly integrated)
   ```bash
   # Install: brew install yices
   ./export/k file.k -yices -timeout 60000
   ```

4. **MathSAT 5** ✅ (newly integrated)
   ```bash
   # Install: Download from https://mathsat.fbk.eu/download.html
   ./export/k file.k -mathsat -timeout 60000
   ```

## Implementation Details

### MiniZinc Integration
- **File**: `src/k/frontend/MiniZincSolver.scala`
- **Timeout**: Implemented via process monitoring with 100ms polling
- **Command-line**: `-mzn-solver <name>` and `-mzn-timeout <ms>`
- **Default timeout**: Uses main `timeoutValue` (30 seconds) if not specified

### SMT Solver Integration Pattern
All SMT solvers follow the same pattern as `CVC5Solver.scala`:
- Auto-detect binary location
- Write SMT-LIB2 model to temp file
- Execute solver with timeout
- Parse sat/unsat/unknown results
- Extract model values if satisfiable
- Fall back to Z3 if solver not available

**New files:**
- `src/k/frontend/YicesSolver.scala`
- `src/k/frontend/MathSATSolver.scala`

## Testing Results on DSN_Pass.k

All solvers tested with 60-second timeout:

| Solver | Result | Notes |
|--------|--------|-------|
| Z3 | Timeout | Default 30s timeout |
| CVC5 | Very slow | Not completed |
| MiniZinc + Gecode | Timeout | 60s timeout |
| MiniZinc + HiGHS | Timeout | 60s timeout |
| MiniZinc + COIN-BC | Timeout | 60s timeout |
| MiniZinc + SCIP | Timeout | 60s timeout |
| MiniZinc + OR-Tools CP-SAT | Timeout | 60s timeout |
| Yices 2 | Not tested | Requires installation |
| MathSAT 5 | Not tested | Requires installation |

## Next Steps

1. **Install and test Yices 2:**
   ```bash
   brew install yices
   ./export/k src/examples/DSN_Pass.k -yices -timeout 60000
   ```

2. **Install and test MathSAT 5:**
   - Download from https://mathsat.fbk.eu/download.html
   - Add to PATH or set `MathSATSolver.mathsatPath`
   - Test: `./export/k src/examples/DSN_Pass.k -mathsat -timeout 60000`

3. **Consider problem reformulation:**
   - Since all solvers struggle, the problem structure may be the issue
   - Use incremental solving to isolate problematic constraints
   - Consider decomposition or relaxation strategies

## Command Reference

```bash
# MiniZinc with specific solver and timeout
./export/k file.k -minizinc -mzn-solver <solver> -mzn-timeout <ms>

# SMT solvers
./export/k file.k -cvc5 -timeout <ms>
./export/k file.k -yices -timeout <ms>
./export/k file.k -mathsat -timeout <ms>

# Default Z3
./export/k file.k -timeout <ms>
```

