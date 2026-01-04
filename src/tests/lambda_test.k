-- Test: lambda_test.k
-- Tests lambda expressions (pattern -> expression)
-- Note: Lambda parsing works, but type checker doesn't yet handle variable binding
-- @expected ERROR

class Calculator {
  -- Lambda as function value (parses correctly but type checker fails on 'x')
  addOne : Int -> Int
  req addOne = (x -> x + 1)
}

c : Calculator
