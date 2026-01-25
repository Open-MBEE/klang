# external3.k Analysis

## Purpose
Tests external Java function calls with import statement.

## Current State
- Expected: SAT with sqrt(100.0) = 10.0
- Actual: SAT with x=2.0
- Status: **CRITICAL BUG - Import Breaks External Function**

## Issue: IMPORT STATEMENT BREAKS MATH.SQRT

**Problem**: `import java.lang.Math; x = Math.sqrt(100.0)` returns x=2.0
**Expected**: sqrt(100.0) = 10.0
**Actual**: x = 2.0

**Root Cause**: When using import statement, the function resolution fails.
The value 2.0 appears to be a default/arbitrary value.

## Severity: CRITICAL
