# external2.k Analysis

## Purpose
Tests external Java function calls with symbolic arguments (sqrt).

## Current State
- Expected: SAT
- Actual: SAT with x=0.50, y=2.0
- Status: **CRITICAL BUG - Wrong Function Value**

## Issue: SQRT RETURNS WRONG VALUE

**Problem**: `y = java.lang.Math.sqrt(x)` with x=0.50 returns y=2.0
**Expected**: sqrt(0.5) ≈ 0.707
**Actual**: y = 2.0

**Impact**: External functions with symbolic arguments return garbage values.

## Severity: CRITICAL
