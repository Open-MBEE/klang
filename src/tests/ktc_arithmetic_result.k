-- Test: KTypeChecker should catch arithmetic result type mismatch
-- (x + y) returns Int, can't assign to Bool
-- @expected ERROR

x : Int = 5
y : Int = 3
z : Bool = (x + y)  -- Should fail: Int assigned to Bool
