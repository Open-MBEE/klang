# Unified Solver General Algorithm

## Overview

The unified solver implements a general max-SAT algorithm that should work for any model, including disjunctive structures like DSN_Pass.k, without requiring hardcoded scenario detection.

## General Algorithm

### Core Strategy: Incremental Max-SAT with Optimize API

1. **Use Z3 Optimize API** (not standard solver)
   - Provides better partial model support on timeout
   - Supports soft constraints for max-SAT
   - Can return best-effort solutions

2. **Group Constraints by Name**
   - Use `IncrementalDiagnostic.groupAssertionsByConstraint` to group related constraints
   - Groups are identified by constraint names in the SMT model (from `UtilSMT.constraintMessageMap`)

3. **Incremental Constraint Addition**
   - Add base constraints first (type checking, heap structure, etc.)
   - Add named constraint groups incrementally
   - Check satisfiability periodically (every 10% of groups or after each group)

4. **Adaptive Soft Constraints**
   - When a constraint group causes UNSAT, mark it as "problematic"
   - If the final check is UNSAT, rebuild the problem with problematic groups as soft constraints
   - This allows finding best-effort solutions that satisfy as many constraints as possible

5. **Best-Effort Solutions**
   - On timeout, return the best partial model found so far
   - Verify the partial model against all hard constraints
   - Report if it's a true solution or a best-effort solution

## How This Should Handle DSN_Pass.k

DSN_Pass.k has a large disjunctive constraint (`Nominal_Anomaly_Impact`) with three branches:
- Nominal: `missedpass = false`
- Anomalous Tolerable: `missedpass = true && tolerate = true`
- Anomalous Not Tolerable: `missedpass = true && tolerate = false`

**Without hardcoded scenario detection**, the algorithm should:

1. **Z3 naturally explores disjunctions**: The SMT encoding of `||` means Z3 will explore all branches
2. **Incremental addition helps**: By adding constraints incrementally, we can identify which constraint groups are problematic
3. **Soft constraints provide fallback**: If some groups cause UNSAT, making them soft allows finding solutions that satisfy other groups
4. **Best-effort on timeout**: If the full problem times out, return the best partial solution found

## Why It Currently Doesn't Work

The current `solveWithTimeout` method (used without scenario tracking) just uses the standard Z3 solver:
- No Optimize API
- No incremental constraint addition
- No adaptive soft constraints
- No best-effort solutions

This means DSN_Pass.k times out because:
- The full disjunction is too complex for Z3 to explore within the timeout
- No incremental approach to identify problematic constraints
- No fallback to best-effort solutions

## Solution

Make `solveWithTimeout` use the same Optimize API + incremental approach as `solveWithTimeoutAndAssumption`, but without scenario assumptions:

1. Use Optimize API instead of standard solver
2. Group constraints incrementally
3. Add constraints in batches
4. Make problematic groups soft when they cause UNSAT
5. Return best-effort solutions on timeout

This is a **general algorithm** that works for any model, not just DSN_Pass.k.

