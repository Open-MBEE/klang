-- Test: opaque4.k
-- Tests opaque min/max functions
-- Expected: PASSED

a : Int
b : Int
minVal : Int
maxVal : Int

req a >= 0
req a <= 100
req b >= 0
req b <= 100

-- Use external min/max
minVal = java.lang.Math.min(a, b)
maxVal = java.lang.Math.max(a, b)

-- Constrain the results
req minVal = 10
req maxVal = 50

-- This forces a=10,b=50 or a=50,b=10

