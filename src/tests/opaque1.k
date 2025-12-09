-- Test: opaque1.k
-- Tests opaque function with symbolic argument using CEGAR refinement.
--
-- INTERESTING BEHAVIOR: This test passes when x and y are Int, but fails
-- when they are Real. With Int, Z3 is restricted to integer values, so
-- CEGAR refinement eventually finds x=25 (since there are only 100 integers
-- to try in the range [1,100]). With Real, Z3 can pick any real number,
-- so it gets stuck doing binary search around x=20 and never finds x=25.
--
-- This demonstrates that CEGAR works better with discrete/bounded domains.
-- For Real-valued problems with external functions, use one of these approaches:
--   1. Define the function relationship in K (see opaque2.k):
--      req y >= 0 && y * y = x  -- instead of y = sqrt(x)
--   2. Provide function specifications with @requires/@ensures
--   3. Use incremental solving with domain knowledge
--
-- Expected: PASSED (x=25, y=5)
x : Int
y : Int
req x >= 1
req x <= 100
req y = java.lang.Math.sqrt(x)
req y = 5

