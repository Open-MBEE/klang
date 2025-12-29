-- PENDING FEATURE: Synthetic Function Calls
-- See FEATURE_ROADMAP.md for details
-- 
-- This test demonstrates a function with an unsatisfiable precondition (req x && !x).
-- Currently returns SAT because the function is never called.
-- Once synthetic function call instantiation is implemented, this should return UNSAT.
--
-- @expected SAT

fun f(x : Bool) : Int {
  req x && !x
  if x then 1 else 0
}
