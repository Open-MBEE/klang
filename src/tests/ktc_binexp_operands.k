-- Test: KTypeChecker should catch binary expression operand type mismatch
-- Can't compare Int with String
-- @expected ERROR

x : Int = 5
y : String = "hello"

req x < y  -- Should fail: can't compare Int with String
