-- Test: external6.k
-- Tests CEGAR refinement with symbolic arguments
-- Expected: PASSED (solver finds consistent values)

-- This test verifies that the CEGAR loop works:
-- 1. Z3 picks values for x and y (sqrt(x) as uninterpreted)
-- 2. When we verify, we check if java.lang.Math.sqrt(x) == y
-- 3. If not, we add refinement constraint and re-solve

class CEGARTest {
  x : Real
  y : Real
  z : Real

  -- x must be a perfect square (4, 9, 16, 25, ...)
  -- The solver should eventually find values where sqrt(x) = y holds
  req x > 0.0
  req x < 100.0

  -- y should be the square root of x
  req y = java.lang.Math.sqrt(x)

  -- z should be y squared (which should equal x)
  req z = y * y

  -- After CEGAR refinement, z should approximately equal x
  -- (within floating point tolerance)
  req z > x - 0.001
  req z < x + 0.001
}

test : CEGARTest

