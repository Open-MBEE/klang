-- Test: opaque3.k
-- Tests opaque function with integer operations
-- Expected: PASSED

x : Int
y : Int
z : Int

req x >= -100
req x <= 100

-- Absolute value
y = java.lang.Math.abs(x)

-- Find x where |x| = 42
req y = 42

-- z should be positive (abs is always >= 0)
z = java.lang.Math.abs(x + 10)
req z >= 0

