-- Test: python_external3.k
-- Tests Python math with Pythagorean theorem (matching external7.k Java version)
-- Expected: PASSED

import python math

class PythagoreanPython {
  -- Use Python math module for Pythagorean theorem

  a : Real
  b : Real
  c : Real

  -- Pythagorean theorem: a^2 + b^2 = c^2
  req a = 3.0
  req b = 4.0
  req c = math.sqrt(math.pow(a, 2.0) + math.pow(b, 2.0))

  -- c should be 5.0 (3-4-5 right triangle)
  req c = 5.0
}

main : PythagoreanPython

