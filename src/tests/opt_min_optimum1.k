// @expected SAT
// Test: minimize with lower bound not at zero (expect x=37)
class OptMin1 {
  x : Int
  req x >= 37
  req x <= 1000
  minimize x
}
main : OptMin1
