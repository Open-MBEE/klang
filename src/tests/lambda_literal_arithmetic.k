-- @expected SAT
-- @preferred_options -cvc5
-- Test: lambda_literal_arithmetic.k
-- Lambda with arithmetic expressions
--
-- STATUS: Returns UNKNOWN (heap getter limitation)

double : Int -> Int
square : Int -> Int
addTen : Int -> Int

req double = (x -> x * 2)
req square = (x -> x * x)
req addTen = (n -> n + 10)
