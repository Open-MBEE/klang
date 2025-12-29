-- IMPLEMENTED: Synthetic Function Calls
-- See FEATURE_ROADMAP.md for details
-- 
-- This test demonstrates a function with an unsatisfiable precondition (req x && !x).
-- The synthetic function call feature generates a call to the function and detects
-- that the body constraint is unsatisfiable.
--
-- @expected UNSAT

fun f(x : Bool) : Int {
  req x && !x
  if x then 1 else 0
}
