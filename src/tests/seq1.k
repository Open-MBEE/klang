-- Test: seq1.k
-- Tests basic Seq operations with Z3 sequence theory
-- Expected: PASSED

-- Top-level sequence variable
numbers : Seq[Int]

-- Constraint on sequence length
req numbers.size >= 2
req numbers.size <= 5

-- First element constraint
req numbers.at(0) > 0

-- Second element constraint
req numbers.at(1) > numbers.at(0)

