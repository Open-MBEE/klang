-- Test: object_bounds2.k
-- Tests sequences with more complex constraints
-- Expected: PASSED

-- Two sequences that must be related
xs : Seq[Int]
ys : Seq[Int]

-- Size constraints
req xs.size >= 2
req xs.size <= 3
req ys.size = xs.size  -- Same size

-- Elements of xs must be positive
req xs.at(0) > 0
req xs.at(1) > 0

-- ys elements are twice xs elements
req ys.at(0) = xs.at(0) * 2
req ys.at(1) = xs.at(1) * 2

-- xs must be sorted
req xs.at(1) > xs.at(0)

