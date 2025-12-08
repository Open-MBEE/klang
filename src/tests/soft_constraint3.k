-- Test: soft_constraint3.k
-- Tests mixing hard and soft constraints
-- Expected: PASSED

class MixedConstraints {
  value : Int

  -- Hard constraints
  req value >= 50
  req value <= 150

  -- Soft constraint that can be satisfied
  soft req value >= 100  -- prefer higher values

  -- Another soft constraint
  soft req value <= 120  -- but not too high
}

test : MixedConstraints
-- Should pass with value in [100, 120] satisfying all constraints

