// @expected SAT
// Test: hard req beats heavy soft; remaining soft picks x in [3,4]
class SoftHard1 {
  x : Int
  req x <= 4
  req x >= 0
  soft req x >= 9 weight 100
  soft req x >= 3 weight 1
}
main : SoftHard1
