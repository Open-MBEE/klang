-- @expected SAT
-- Test: Three-way mutual recursion (cycle: f1 -> f2 -> f3 -> f1)
-- Computes factorial via a circular chain

fun f1(n: Int) : Int {
  if n <= 1 then 1
  else n * f2(n - 1)
}

fun f2(n: Int) : Int {
  if n <= 1 then 1
  else n * f3(n - 1)
}

fun f3(n: Int) : Int {
  if n <= 1 then 1
  else n * f1(n - 1)
}

result: Int = f1(6)

req result = 720
