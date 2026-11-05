// @expected SAT
// Test: weight 0 soft is ignored in favor of weight 1 (expect x=2)
class SoftZero1 {
  x : Int
  req x >= 0
  req x <= 10
  soft req x = 9 weight 0
  soft req x = 2 weight 1
}
main : SoftZero1
