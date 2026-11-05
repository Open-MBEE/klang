// @expected SAT
// Test: soft constraint combined with objective (expect x=5, y=0)
class SoftPlusObjective1 {
  x : Int
  y : Int
  req x >= 0
  req x <= 5
  req y >= 0
  req y <= 5
  req x + y <= 5
  soft req x >= 5 weight 2
  minimize y
}
main : SoftPlusObjective1
