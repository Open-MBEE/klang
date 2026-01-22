-- @expected SAT
-- @preferred_options -cvc5
-- Test: lambda_initializer.k
-- Lambda as property initializer - should use direct const constraint

f : Int -> Int = x -> x + 1
