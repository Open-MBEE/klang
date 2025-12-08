-- Test: opaque1.k
-- Tests opaque function with symbolic argument
-- Expected: PASSED (x should be 25.0 since sqrt(25) = 5)
x : Real
y : Real
req x >= 1.0
req x <= 100.0
req y = java.lang.Math.sqrt(x)
req y = 5.0

