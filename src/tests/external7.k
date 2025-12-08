-- Test: external7.k
-- Tests import statement with multiple external calls
-- Expected: PASSED

import java.lang.Math

class ImportTest {
  -- Use imported Math class with simple name

  a : Real
  b : Real
  c : Real

  -- Pythagorean theorem: a^2 + b^2 = c^2
  req a = 3.0
  req b = 4.0
  req c = Math.sqrt(Math.pow(a, 2.0) + Math.pow(b, 2.0))

  -- c should be 5.0 (3-4-5 right triangle)
  req c = 5.0
}

main : ImportTest

