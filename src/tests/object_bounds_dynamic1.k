-- Test: object_bounds_dynamic1.k
-- Tests sequences with sum-like constraints
-- Expected: PASSED

-- Task durations as a sequence
durations : Seq[Int]
priorities : Seq[Int]

-- We have 2-4 tasks
req durations.size >= 2
req durations.size <= 4
req priorities.size = durations.size

-- All durations positive and bounded
req durations.at(0) > 0
req durations.at(0) <= 100
req durations.at(1) > 0
req durations.at(1) <= 100

-- All priorities in range 1-5
req priorities.at(0) >= 1
req priorities.at(0) <= 5
req priorities.at(1) >= 1
req priorities.at(1) <= 5

-- Total duration constraint (for first two tasks)
req durations.at(0) + durations.at(1) <= 150

-- At least one high priority task
req priorities.at(0) = 5 || priorities.at(1) = 5

