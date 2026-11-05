// @expected SAT
// Test: weights across objectives: b has weight 3 so b=10, a=0
class OptWeighted1 {
  a : Int
  b : Int
  req a >= 0
  req b >= 0
  req a + b <= 10
  maximize a weight 1
  maximize b weight 3
}
main : OptWeighted1
