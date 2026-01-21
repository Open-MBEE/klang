-- @expected SAT
-- Test: Mutual recursion with three-way cycle computing factorial
-- f1 -> f2 -> f3 -> f1 in a cycle, but computing factorial via alternating
--
-- Now supported via define-funs-rec in SMT-LIB2!

// Mutually recursive functions computing factorial via a three-way cycle
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

r2: Int = f1(8)

req r2 = 40320
