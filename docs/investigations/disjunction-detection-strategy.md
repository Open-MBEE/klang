# Disjunction Detection Strategy for Unified Solver

## Overview

This document explains how the unified solver can detect and exploit disjunctive structure in constraint groups to solve hard problems more efficiently.

## What Worked Earlier

For DSN_Pass.k, we had hardcoded scenario detection that:
1. Identified 3 scenarios (Nominal, AnomalousTolerable, AnomalousNotTolerable)
2. Tried each scenario separately using assumptions
3. Within each scenario, used incremental constraint addition
4. Used adaptive soft constraints when constraint groups caused UNSAT

This worked because it avoided combinatorial explosion by exploring branches separately.

## General Strategy

The general algorithm should:

### 1. Detect Disjunctive Structure

For each constraint group:
- Check if it has a single assertion that is a top-level OR expression
- Extract the branches (handling nested ORs)
- Consider it "disjunctive" if it has 2-10 non-trivial branches

### 2. Handle Disjunctive Groups

For disjunctive groups:
- Try each branch separately as an assumption
- Within each branch, use incremental constraint addition + soft constraints
- Eliminate UNSAT branches early
- Continue with remaining viable branches

### 3. Handle Non-Disjunctive Groups

For regular (non-disjunctive) groups:
- Use incremental constraint addition
- Use adaptive soft constraints when groups cause UNSAT
- This works for any hard problem, not just disjunctive ones

## Implementation

The `DisjunctionDetector` analyzes constraint groups to detect top-level OR expressions. When detected:

1. The unified solver splits the group into branches
2. Each branch is tried separately using Z3 assumptions
3. Within each branch, the normal incremental + soft constraint strategy applies
4. This combines the benefits of:
   - Branch splitting (avoids combinatorial explosion)
   - Incremental addition (identifies problematic constraints)
   - Soft constraints (finds best-effort solutions)

## Benefits

- **General**: Works for any problem with disjunctive structure, not just DSN_Pass.k
- **Efficient**: Avoids exploring all combinations of disjunctive branches
- **Robust**: Falls back to incremental + soft constraints within each branch
- **Best-effort**: Can still find partial solutions on timeout

