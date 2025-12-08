-- Test: opaque2.k
-- Tests multiple opaque function calls with interdependent constraints
-- Expected: PASSED

a : Real
b : Real
c : Real

req a >= 1.0
req a <= 10.0
req b >= 1.0
req b <= 10.0

-- c = a^2 + b^2 (Pythagorean)
c = java.lang.Math.sqrt(java.lang.Math.pow(a, 2.0) + java.lang.Math.pow(b, 2.0))

-- Find a Pythagorean triple: 3-4-5
req c = 5.0

