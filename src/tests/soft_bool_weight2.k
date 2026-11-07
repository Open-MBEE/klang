// @expected SAT
// Test: two heavier softs outweigh one light conflicting soft (expect a=true, b=true, c=false)
class SoftBool2 {
  a : Bool
  b : Bool
  c : Bool
  req c = !(a && b)
  soft req c weight 1
  soft req a weight 2
  soft req b weight 2
}
main : SoftBool2
