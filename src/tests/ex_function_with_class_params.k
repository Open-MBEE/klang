-- Test: Function returning constructor call
--
-- This test verifies that functions can return constructor calls.
-- The fix detects when a function body contains a return statement with a
-- constructor call and uses 'Any' as the return type (since constructors
-- generate (lift-X (mk-X ...)) which has type Any).
--
-- The function sum() takes two X parameters and returns a new X with
-- value = x1.value + x2.value. The function is defined but not called,
-- so there are no constraints forcing the result to satisfy X's invariant.
--
-- @expected SAT

class X {
  value : Int
  req value > 0 && value < 3
}

fun sum(x1 : X, x2: X) : X {
  req x1.value != x2.value
  return X(value::x1.value+x2.value)
}
