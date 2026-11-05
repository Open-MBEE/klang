// @expected SAT
// Test: named soft constraint with weight (expect y=7)
class SoftNamedWeight1 {
  y : Int
  req y >= 0
  req y <= 10
  soft req pick: y = 7 weight 3
  soft req other: y = 2 weight 1
}
main : SoftNamedWeight1
