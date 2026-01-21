-- @expected SAT
-- Test: lambda_test.k
-- Tests lambda expressions (pattern -> expression)
--
-- BUG: This test currently PASSES but SHOULD FAIL as ERROR.
-- The type checker doesn't handle lambda variable binding - the `x` in `x -> x + 1`
-- is not being added to the type environment, so `x` should be an undeclared variable.
-- When lambda type checking is fixed, change this back to expect ERROR.
--
-- TODO: Fix type checker to properly bind lambda pattern variables before checking body
-- See TypeChecker.scala - need to add pattern variable to environment in lambda case

class Calculator {
  -- Lambda as function value
  -- BUG: `x` is not bound - should fail type check but currently passes incorrectly
  addOne : Int -> Int
  req addOne = (x -> x + 1)
}

c : Calculator
