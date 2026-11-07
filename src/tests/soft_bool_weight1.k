// @expected SAT
// Test: weighted soft constraints on Bools; a and b conflict, heavier wins (expect a=false, b=true)
class SoftBool1 {
  a : Bool
  b : Bool
  req !(a && b)
  soft req a weight 1
  soft req b weight 4
}
main : SoftBool1
