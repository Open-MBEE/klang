# Incremental Constraint Addition + Adaptive Soft Constraints Strategy

## Overview

Parts 2 and 3 (incremental constraint addition and adaptive soft constraints) work for **any hard problem**, not just those with disjunctive structure. This document explains how this strategy works and how it can be combined with disjunction detection.

## How It Works

### Part 2: Incremental Constraint Addition

1. **Group Constraints**: Constraints are grouped by name using `IncrementalDiagnostic.groupAssertionsByConstraint`
2. **Add Incrementally**: Groups are added one at a time (or in small batches)
3. **Check Progress**: After each batch, we check satisfiability
4. **Track State**: We track which groups are added and maintain the best model so far

### Part 3: Adaptive Soft Constraints

1. **Identify Problematic Groups**: When a group causes UNSAT, it's marked as "problematic"
2. **Rebuild with Soft Constraints**: If the final check is UNSAT, rebuild the problem with:
   - Non-problematic groups as **hard constraints**
   - Problematic groups as **soft constraints** (using Z3 Optimize API)
3. **Max-SAT**: The Optimize solver finds a solution that satisfies as many constraints as possible
4. **Best-Effort Solutions**: On timeout, return the best partial model found so far

## Why This Works for Any Hard Problem

- **Incremental Addition**: Identifies which constraint groups are causing problems
- **Soft Constraints**: Allows finding solutions that satisfy most constraints even if some are impossible
- **Best-Effort**: Provides partial solutions on timeout, which may still be useful
- **No Problem-Specific Logic**: Works for any constraint structure, including disjunctions

## How It Handles Disjunctions

Z3 naturally explores disjunctions (OR expressions) in the SMT encoding. The incremental + soft constraint strategy helps by:

1. **Breaking Down Complexity**: Adding constraints incrementally makes each check smaller
2. **Identifying Bottlenecks**: Problematic constraint groups can be identified even within disjunctions
3. **Soft Constraints**: If some branches of a disjunction are impossible, making them soft allows finding solutions in other branches

## Combination with Disjunction Detection

For problems with **large top-level disjunctions** (like DSN_Pass.k), we can improve performance by:

1. **Detecting Disjunctive Structure**: Identify constraint groups with top-level OR expressions
2. **Splitting Branches**: Try each disjunctive branch separately (using assumptions or by splitting)
3. **Applying Strategy Per Branch**: Within each branch, use incremental + soft constraints
4. **Early Elimination**: UNSAT branches can be eliminated early, avoiding wasted computation

This combines:
- **Branch Splitting**: Avoids combinatorial explosion of disjunctive branches
- **Incremental Addition**: Identifies problematic constraints within each branch
- **Soft Constraints**: Finds best-effort solutions even when some constraints conflict

## Current Implementation

The current `solveWithTimeout` method in `UnifiedSolver.scala` implements Parts 2 and 3:

- Uses Z3 Optimize API for better partial model support
- Groups constraints by name
- Adds constraints incrementally
- Makes problematic groups soft when they cause UNSAT
- Returns best-effort solutions on timeout

This should work for any hard problem, including those with disjunctions. However, for problems with very large disjunctions, explicitly detecting and splitting branches can provide better performance.

