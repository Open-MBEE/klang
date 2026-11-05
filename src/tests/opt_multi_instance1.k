// @expected SAT
// Test: each instance optimized by its own objective (a: x=10, b: x=1)
class OptMI {
  x : Int
  req x >= 1
  req x <= 10
}
class OptMax extends OptMI { maximize x }
class OptMin extends OptMI { minimize x }
a : OptMax
b : OptMin
