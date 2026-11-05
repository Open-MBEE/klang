// @expected SAT
// Test: maximize x where x = 2*k, bounded (expect x=98, k=49)
class OptEven1 {
  x : Int
  k : Int
  req k >= 0
  req x = 2 * k
  req x <= 99
  maximize x
}
main : OptEven1
