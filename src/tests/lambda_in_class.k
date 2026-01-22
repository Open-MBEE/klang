-- @expected SAT
-- Test: lambda_in_class.k
-- Lambda-equivalent using Array inside a class (this WORKS today)

class MathFunctions {
  -- Functions as arrays
  double : Array[Int, Int]
  square : Array[Int, Int]
  
  -- Define behavior at key points
  req double[0] = 0
  req double[1] = 2
  req double[5] = 10
  req double[10] = 20
  
  req square[0] = 0
  req square[1] = 1
  req square[2] = 4
  req square[3] = 9
  req square[10] = 100
}

m : MathFunctions

-- Use the functions
x : Int
req x > 0
req x < 5
req m.double[x] = 2 * x
req m.square[x] = x * x
