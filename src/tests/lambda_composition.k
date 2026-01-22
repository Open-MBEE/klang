-- @expected SAT
-- Test: lambda_composition.k
-- Function composition using Array (this WORKS today)

-- Two functions
f : Array[Int, Int]  -- f(x) = x + 1
g : Array[Int, Int]  -- g(x) = x * 2

-- Define f at points
req f[0] = 1
req f[5] = 6
req f[10] = 11

-- Define g at points  
req g[0] = 0
req g[1] = 2
req g[6] = 12

-- Composition: g(f(x)) should work
x : Int
req x = 5
req g[f[x]] = 12  -- g(f(5)) = g(6) = 12
