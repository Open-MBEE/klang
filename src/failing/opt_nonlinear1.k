// @expected SAT
// KNOWN LIMITATION: Z3 Optimize reports SAT but returns non-optimal x=6 for this nonlinear objective (true optimum x=7)
class OptNonlinear1 {
  x : Int
  req x >= 0
  req x <= 10
  fun cost(v : Int) : Int { 100 - (v - 7) * (v - 7) }
  maximize cost(x)
}
main : OptNonlinear1
