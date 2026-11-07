// @expected SAT
// Test: non-numeric soft constraints combined with a numeric objective (expect on=true, n=10)
class SoftMixed1 {
  on : Bool
  n : Int
  req n >= 0
  req n <= 10
  req on = (n > 5)
  soft req on weight 3
  soft req !on weight 1
  maximize n
}
main : SoftMixed1
