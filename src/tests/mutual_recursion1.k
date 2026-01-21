-- @expected SAT
-- Test: Simple mutual recursion between two functions
-- f1 and f2 alternate in computing an even/odd check

fun isEven(n: Int) : Bool {
  if n = 0 then true
  else isOdd(n - 1)
}

fun isOdd(n: Int) : Bool {
  if n = 0 then false
  else isEven(n - 1)
}

result1: Bool = isEven(4)
result2: Bool = isOdd(5)

req result1 = true
req result2 = true
