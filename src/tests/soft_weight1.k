// @expected SAT
// Test: heavier soft constraint wins (expect x=8)
class SoftWeight1 {
  x : Int
  req x >= 0
  req x <= 10
  soft req x >= 8 weight 5
  soft req x <= 2 weight 1
}
main : SoftWeight1
