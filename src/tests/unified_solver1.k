-- Test: unified_solver1.k
-- Tests unified solver with basic constraints
-- Expected: PASSED
-- Use: ./export/k -unified src/tests/unified_solver1.k

x : Int
y : Int

req x > 0
req x < 10
req y = x * 2
req y > 5

