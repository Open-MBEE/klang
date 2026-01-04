-- Test: KTypeChecker should catch comparison result type mismatch
-- (x > 3) returns Bool, can't assign to Int
-- @expected ERROR

x : Int = 5
z : Int = (x > 3)  -- Should fail: Bool assigned to Int
