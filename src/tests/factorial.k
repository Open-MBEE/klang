-- @expected UNKNOWN
-- NOTE: Mutual recursion is not yet supported in K.
-- K generates define-fun-rec for self-recursive functions, but SMT-LIB2 requires
-- define-funs-rec (plural) for mutually recursive functions to declare all of them
-- together. This test documents this limitation.
--
-- See factorial_simple.k for a working self-recursive example.

// Mutually recursive functions - NOT YET SUPPORTED
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
