// @expected SAT
// Test: maximize must find true optimum, not first solution (expect x=100, y=0)
class OptMax1 {
  x : Int
  y : Int
  req x >= 0
  req x <= 100
  req y >= 0
  req x + y <= 100
  maximize x
}
main : OptMax1
