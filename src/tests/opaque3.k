-- Test: opaque3.k
-- Tests K-defined function equated to external Java function
-- This demonstrates the pattern for providing Z3 with knowledge about
-- external functions by expressing their mathematical relationships.
-- Expected: PASSED (x should be 25.0 since sqrt(25) = 5)

-- The approach: Instead of calling java.lang.Math.sqrt(x) directly,
-- we introduce an intermediate variable sqrt_x and constrain it with
-- the mathematical definition of sqrt.

x : Real
sqrt_x : Real

req x >= 1.0
req x <= 100.0

-- Mathematical definition of sqrt: sqrt_x is the sqrt of x means
-- sqrt_x >= 0 and sqrt_x * sqrt_x = x
req sqrt_x >= 0.0
req sqrt_x * sqrt_x = x

-- Now if we want to verify java.lang.Math.sqrt matches our definition,
-- we can add: req sqrt_x = java.lang.Math.sqrt(x)
-- But for this test, we just use the K-defined relationship.

req sqrt_x = 5.0
