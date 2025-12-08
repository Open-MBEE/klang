-- Test: soft_constraint1.k
-- Tests soft constraint syntax (soft req)
-- Expected: PASSED

class Preference {
  x : Int
  y : Int

  -- Hard constraints (must be satisfied)
  req x >= 0
  req x <= 100
  req y >= 0
  req y <= 100

  -- Soft constraint (preference, may be relaxed)
  soft req x + y = 50

  -- Another soft constraint
  soft req x = y
}

pref : Preference

