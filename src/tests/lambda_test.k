-- @expected SAT
-- @preferred_options -cvc5
-- Test: lambda_test.k
-- Primary test for lambda literal syntax (pattern -> expression)
--
-- Lambda type checking works correctly - pattern variables are bound
-- before type-checking the lambda body.
--
-- CURRENT STATUS: Returns UNKNOWN because constraints go through heap
-- getter functions. CVC5 CAN solve lambda literals when constraints
-- are applied directly to const objects (verified with raw SMT).
--
-- RELATED TESTS:
-- - lambda_basic_array.k     - SAT: Array-based function (no lambda literal)
-- - lambda_in_class.k        - SAT: Functions as arrays in class
-- - lambda_composition.k     - SAT: Function composition with arrays
-- - lambda_literal_simple.k  - UNKNOWN: Simple lambda literal
-- - lambda_literal_arithmetic.k - UNKNOWN: Various arithmetic lambdas
-- - lambda_literal_bool.k    - UNKNOWN: Predicate lambdas
--
-- FIX: Generate constraints using const directly instead of getters
-- for function-typed properties. See .tmp/lambda_direct_const.smt2

class Calculator {
  addOne : Int -> Int
  req addOne = (x -> x + 1)
}

c : Calculator
