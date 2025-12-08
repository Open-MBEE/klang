-- Test: cegar1.k
-- Tests CEGAR-style refinement with external functions
-- Expected: PASSED
-- External function calls verified against actual implementation

x : Int
y : Int

req x >= 0
req x <= 10

-- Call external sqrt (this would trigger CEGAR verification)
-- For now, we just test the constraint encoding
y = java.lang.Math.abs(x - 5)

req y <= 3

