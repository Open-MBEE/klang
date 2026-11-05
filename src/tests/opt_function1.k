// @expected SAT
// Test: objective through a function call (expect x=10)
class OptFun1 {
  x : Int
  req x >= 0
  req x <= 10
  fun cost(v : Int) : Int { 3 * v - 5 }
  maximize cost(x)
}
main : OptFun1
