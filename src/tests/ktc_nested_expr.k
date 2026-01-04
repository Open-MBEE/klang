-- Test: KTypeChecker should catch nested expression type mismatch
-- Real + String should fail
-- @expected ERROR

x : Real = 1.0
y : String = "hello"
z : Real

req z = x + y  -- Should fail: can't add Real and String
