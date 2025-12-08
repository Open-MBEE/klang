-- Test: object_bounds1.k
-- Tests basic Seq operations - dynamic collection size
-- Expected: PASSED

-- A sequence of integers with constraints
items : Seq[Int]

-- Size constraints - solver determines how many elements
req items.size >= 2
req items.size <= 4

-- All elements must be positive
req items.at(0) > 0
req items.at(1) > 0

-- Elements must be increasing
req items.at(1) > items.at(0)

