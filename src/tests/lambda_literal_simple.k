-- @expected SAT
-- @preferred_options -cvc5
-- Test: lambda_literal_simple.k
-- Simple lambda literal - tests the syntax works through type checker
--
-- Lambda constraints now solve correctly with direct const constraint generation

f : Int -> Int
req f = (x -> x + 1)
