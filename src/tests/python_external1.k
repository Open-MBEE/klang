-- Test: python_external1.k
-- Tests import of Python math module
-- Expected: PASSED

import python math

class PythonMathTest {
  -- Test basic math functions from Python's math module

  x : Real
  y : Real
  result : Real

  -- Use Python's sqrt function
  req x = 25.0
  req result = math.sqrt(x)

  -- result should be 5.0
  req result = 5.0
}

main : PythonMathTest

