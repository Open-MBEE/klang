-- Test: soft_constraint4.k
-- Tests soft constraints with named constraints
-- Expected: PASSED

class NamedSoftConstraints {
  budget : Int
  quality : Int
  speed : Int

  -- Hard constraints
  req budget >= 0
  req budget <= 1000
  req quality >= 1
  req quality <= 10
  req speed >= 1
  req speed <= 10

  -- Named soft constraints (preferences)
  soft req lowCost: budget < 500
  soft req highQuality: quality >= 8
  soft req fast: speed >= 7

  -- Trade-off: can't have all three ideally
  -- Higher quality usually costs more
  req budget >= quality * 50
}

project : NamedSoftConstraints

