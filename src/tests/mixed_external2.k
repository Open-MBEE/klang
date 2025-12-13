-- Test: mixed_external2.k
-- More complex test with Java and Python working together
-- Demonstrates cross-language constraint solving
-- Expected: PASSED

import java.lang.Math
import python math

class CircleCalculation {
  -- Calculate circle properties using both Java and Python

  radius : Real

  -- Circumference calculated by Java
  circumference_java : Real

  -- Area calculated by Python
  area_python : Real

  -- Given radius
  req radius = 5.0

  -- Java calculates circumference: 2 * PI * r
  -- Note: Math.PI is a constant in java.lang.Math
  req circumference_java = 2.0 * 3.14159265359 * radius

  -- Python calculates area: PI * r^2
  req area_python = math.pi * math.pow(radius, 2.0)

  -- Verify expected values (approximately)
  req circumference_java > 31.0
  req circumference_java < 32.0

  req area_python > 78.0
  req area_python < 79.0
}

class TrigComparison {
  -- Compare trig functions between Java and Python

  angle : Real  -- in radians

  sinJava : Real
  sinPython : Real

  cosJava : Real
  cosPython : Real

  -- Test at PI/4 (45 degrees)
  req angle = 0.7853981633974483  -- PI/4

  -- Java trig
  req sinJava = Math.sin(angle)
  req cosJava = Math.cos(angle)

  -- Python trig
  req sinPython = math.sin(angle)
  req cosPython = math.cos(angle)

  -- At 45 degrees, sin and cos should be equal
  -- (approximately sqrt(2)/2 ≈ 0.7071)
  req sinJava > 0.7
  req sinJava < 0.72
  req sinJava = sinPython
  req cosJava = cosPython
}

circle : CircleCalculation
trig : TrigComparison

