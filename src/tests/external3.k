-- Test: external3.k
-- Tests external Java function calls with import statement
-- Expected: PASSED (sqrt(100.0) = 10.0 using imported Math)

import java.lang.Math

class A {
  x : Real

  -- Using imported Math class (without java.lang prefix)
  req x = Math.sqrt(100.0)
}

a : A

