-- @expected SAT
-- Test: Mixed - some functions are mutually recursive, others are independent
-- This tests that non-recursive and self-recursive functions still work alongside mutual recursion

-- Independent function (not recursive)
fun double(n: Int) : Int {
  n * 2
}

-- Self-recursive function
fun factorial(n: Int) : Int {
  if n <= 1 then 1
  else n * factorial(n - 1)
}

-- Mutually recursive pair
fun ping(n: Int) : Int {
  if n <= 0 then 0
  else 1 + pong(n - 1)
}

fun pong(n: Int) : Int {
  if n <= 0 then 0
  else 1 + ping(n - 1)
}

r1: Int = double(5)
r2: Int = factorial(5)
r3: Int = ping(10)

req r1 = 10
req r2 = 120
req r3 = 10
