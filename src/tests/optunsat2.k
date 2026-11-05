// @expected UNSAT
// Test: maximize on unsatisfiable hard constraints
class OptUnsat2 {
  x : Int
  req x > 10
  req x < 5
  maximize x
}
main : OptUnsat2
