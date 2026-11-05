
class OptNonlinear1 {
  x:Int

  req x >= 0

  req x <= 10

  fun cost(v:Int) : Int
  {
    100 - (v - 7) * (v - 7)
  }
  maximize cost(x)
}

main:OptNonlinear1

