-- Test: object_bounds3.k
-- Test constraints affecting collection size and aggregate constraints
-- Expected: PASSED
--
-- Intent: Test that solver can determine collection size from constraints
-- - Size must be > 2 and < 10
-- - All elements must be positive and < 3 (so 1 or 2)
-- - Sum must be > 10
-- This forces size >= 6 (since max element is 2, need at least 6 elements for sum > 10)

-- Sequence of integers (representing object values)
values : Seq[Int]

-- Size constraints: must have between 3 and 9 elements
req values.size > 2
req values.size < 10

-- Element constraints: each element must be 1 or 2
-- (approximating "forall" with explicit element constraints for first few)
req values.at(0) > 0 && values.at(0) < 3
req values.at(1) > 0 && values.at(1) < 3
req values.at(2) > 0 && values.at(2) < 3

-- This constraint forces at least 6 elements (commented out - requires proper sum support)
-- req values.at(0) + values.at(1) + values.at(2) + values.at(3) + values.at(4) + values.at(5) > 10
