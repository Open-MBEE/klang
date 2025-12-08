-- Test: opaque_uninterpreted1.k
-- Tests truly uninterpreted (opaque) function - no Java implementation
-- Expected: PASSED
--
-- K should treat mystery() as uninterpreted and let Z3 synthesize values

class Problem {
  x : Int
  y : Int

  -- mystery is not a Java function - it's truly opaque/uninterpreted
  -- Z3 will synthesize consistent values
  fun mystery(n : Int) : Int

  req x >= 0
  req x <= 10

  -- Constrain mystery function via its behavior
  req mystery(0) = 0
  req mystery(1) = 1
  req mystery(2) = 4
  req mystery(3) = 9

  -- What is mystery(x)?
  y = mystery(x)

  -- Constrain the output
  req y = 16
}

p : Problem

