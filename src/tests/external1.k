-- Test: external1.k
-- Tests external Java function calls with concrete evaluation
-- Expected: PASSED (sqrt(100.0) = 10.0)

class A {
  x : Real

  -- java.lang.Math.sqrt with concrete argument should be evaluated at compile time
  req x = java.lang.Math.sqrt(100.0)
}

a : A

