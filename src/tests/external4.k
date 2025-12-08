-- Test: external4.k
-- Tests external Java function calls with multiple methods
-- Expected: PASSED (concrete evaluation of various Math functions)

class MathTests {
  -- Test various java.lang.Math methods with concrete values

  -- Power function
  powValue : Real
  req powValue = java.lang.Math.pow(2.0, 3.0)  -- should be 8.0
  req powValue = 8.0

  -- Square root
  sqrtValue : Real
  req sqrtValue = java.lang.Math.sqrt(16.0)  -- should be 4.0
  req sqrtValue = 4.0

  -- Absolute value
  absValue : Real
  req absValue = java.lang.Math.abs(-42.0)  -- should be 42.0
  req absValue = 42.0

  -- Max
  maxValue : Real
  req maxValue = java.lang.Math.max(10.0, 20.0)  -- should be 20.0
  req maxValue = 20.0

  -- Min
  minValue : Real
  req minValue = java.lang.Math.min(10.0, 20.0)  -- should be 10.0
  req minValue = 10.0
}

tests : MathTests

