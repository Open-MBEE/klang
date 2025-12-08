-- Test: opaque1.k
-- Tests opaque function with symbolic argument
-- Expected: PASSED
x : Real
y : Real
req x >= 0.0
req x <= 100.0
req y = java.lang.Math.sqrt(x)
req y = 5.0
