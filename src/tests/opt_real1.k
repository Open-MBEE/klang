// @expected SAT
// Test: maximize over Real (expect x=2.5)
class OptReal1 {
  x : Real
  req x >= 0.0
  req x * 2.0 <= 5.0
  maximize x
}
main : OptReal1
