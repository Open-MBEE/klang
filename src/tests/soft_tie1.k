// @expected SAT
// Test: equal-weight conflicting softs; either value is fine but hard req holds
class SoftTie1 {
  x : Int
  req x >= 0
  req x <= 10
  soft req x = 2
  soft req x = 9
}
main : SoftTie1
