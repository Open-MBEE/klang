-- Test: opaque2.k
-- Tests K-defined function with mathematical relationship
-- The sqrt function is defined with constraints: result * result = input
-- Expected: PASSED (x should be 25.0 since sqrt(25) = 5)

x : Real
y : Real
req x >= 1.0
req x <= 100.0
req y >= 0.0
req y * y = x
req y = 5.0
