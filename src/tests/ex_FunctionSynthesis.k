-- @expected SAT
-- Demo: Function Synthesis
-- Z3 can synthesize the specification of an underspecified function!
--
-- Here we declare a function 'mystery' with partial constraints,
-- and Z3 will find a consistent definition for it.

-- Declare an abstract/uninterpreted function
fun mystery(x: Int): Int

-- Partial specification: we only know some input-output pairs
req mystery(0) = 0
req mystery(1) = 1
req mystery(2) = 4
req mystery(3) = 9

-- Z3 synthesizes a function consistent with these points.
-- It might not guess x^2, but it will be consistent!

-- Let's see what Z3 picks for mystery(4) and mystery(5)
class Query {
  answer4 : Int
  answer5 : Int

  req answer4 = mystery(4)
  req answer5 = mystery(5)

  -- Z3 can pick ANY values since we haven't constrained them
  -- This demonstrates that uninterpreted functions are "free"
  -- to have any behavior outside the specified points.
}

q : Query

-- Try adding more constraints to see Z3 narrow down possibilities:
-- req mystery(4) = 16  -- Uncomment to force x^2 pattern at 4
-- req mystery(5) = 25  -- Uncomment to force x^2 pattern at 5

