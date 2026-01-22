-- @expected SAT
-- @preferred_options -cvc5
-- Test: lambda_literal_bool.k
-- Lambda returning Bool
--
-- STATUS: Returns UNKNOWN (heap getter limitation)

isPositive : Int -> Bool
isZero : Int -> Bool
isEven : Int -> Bool

req isPositive = (x -> x > 0)
req isZero = (x -> x = 0)
-- Note: isEven would need modulo which may not be supported
