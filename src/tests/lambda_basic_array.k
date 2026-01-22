-- @expected SAT
-- Test: lambda_basic_array.k
-- Lambda-equivalent behavior using Array (this WORKS today)
--
-- Since Int -> Int maps to Array[Int, Int] in SMT, we can use
-- Array syntax to get lambda-like behavior that solvers can handle.

-- Simple function as array
addOne : Array[Int, Int]

-- Point-wise constraints (solvable)
req addOne[0] = 1
req addOne[5] = 6  
req addOne[10] = 11
req addOne[100] = 101

-- Verify a computed value
x : Int
y : Int
req x = 42
req y = addOne[x]
req y = 43
