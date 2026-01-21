-- @expected SAT
-- Simple self-recursive factorial function
-- K supports self-recursion via define-fun-rec in SMT-LIB2

fun factorial(n: Int) : Int {
  if n <= 1 then 1
  else n * factorial(n - 1)
}

result: Int = factorial(5)

req result = 120
