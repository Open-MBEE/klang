-- Test: external2.k
-- Tests external Java function calls with symbolic arguments
-- Expected: PASSED (uninterpreted function)

class A {
  x : Real
  y : Real

  req x > 0.0
  req y = java.lang.Math.sqrt(x)
  -- Since x is symbolic, sqrt(x) will be an uninterpreted function
  -- The solver should find some x > 0 and set y to some value
}

a : A

