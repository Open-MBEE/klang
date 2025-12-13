-- Test: mixed_external1.k
-- Tests BOTH Java and Python external functions in the same model
-- This demonstrates K's ability to integrate with multiple languages
-- Expected: PASSED

-- Import Java's Math class
import java.lang.Math

-- Import Python's math module
import python math

class MixedExternalTest {
  -- Test using both Java and Python math functions

  -- Values computed using Java
  javaResult : Real

  -- Values computed using Python
  pythonResult : Real

  -- Both should compute the same thing
  input : Real

  req input = 16.0

  -- Java: use java.lang.Math.sqrt
  req javaResult = Math.sqrt(input)

  -- Python: use math.sqrt
  req pythonResult = math.sqrt(input)

  -- Both should equal 4.0
  req javaResult = 4.0
  req pythonResult = 4.0

  -- And they should be equal to each other
  req javaResult = pythonResult
}

main : MixedExternalTest

