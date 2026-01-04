-- Test: opaque4.k
-- Tests opaque min/max functions with symbolic arguments
-- Note: Since a,b are symbolic, min/max are opaque (not evaluated)
-- The solver will find any satisfying assignment without min/max semantics
-- @expected SAT

a : Int
b : Int
minVal : Int
maxVal : Int

req a >= 0
req a <= 100
req b >= 0
req b <= 100

-- Use external min/max (as constraints)
-- Note: These are opaque since a,b are not concrete
req minVal = java.lang.Math.min(a, b)
req maxVal = java.lang.Math.max(a, b)

-- Basic ordering constraints that should hold
req minVal <= maxVal
req minVal >= 0
req maxVal <= 100

