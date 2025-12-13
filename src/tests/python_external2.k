-- Test: python_external2.k
-- Tests Python math functions matching external3.k (Java version)
-- Expected: PASSED (sqrt(100.0) = 10.0 using Python math)

import python math

class A {
  x : Real

  -- Using Python math module
  req x = math.sqrt(100.0)
}

a : A

