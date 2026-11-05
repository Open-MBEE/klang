// @expected SAT
// Test: soft req through a function call (expect x=6)
class SoftFun1 {
  x : Int
  req x >= 0
  req x <= 10
  fun big(v : Int) : Bool { v > 5 }
  soft req big(x) weight 3
  soft req x <= 2 weight 1
  minimize x
}
main : SoftFun1
