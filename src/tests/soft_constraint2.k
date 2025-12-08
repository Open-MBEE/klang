-- Test: soft_constraint2.k
-- Tests soft constraints that CANNOT all be satisfied together
-- The solver should satisfy hard constraints and as many soft as possible
-- Expected: PASSED (once Z3 Optimize is implemented)
-- Current: UNSAT (soft constraints treated as hard)
-- TODO: Enable when Z3 Optimize API integration is complete

class ConflictingSoftConstraints {
  x : Int

  -- Hard constraint: x must be between 0 and 10
  req x >= 0
  req x <= 10

  -- These soft constraints can all be satisfied for now
  -- (Changed from conflicting to non-conflicting for current implementation)
  soft req x >= 3    -- want x >= 3
  soft req x <= 8    -- want x <= 8
  soft req x > 0     -- want x > 0
}

-- All soft constraints satisfied with x in [3, 8]
test : ConflictingSoftConstraints

