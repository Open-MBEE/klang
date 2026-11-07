// @expected SAT
// Test: weighted soft constraints on String (expect s = "blue")
class SoftString1 {
  s : String
  req s = "red" || s = "green" || s = "blue"
  soft req s = "red" weight 1
  soft req s = "blue" weight 5
  soft req s = "green" weight 2
}
main : SoftString1
