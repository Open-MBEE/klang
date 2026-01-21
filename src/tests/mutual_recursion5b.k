-- @expected ERROR
-- Helper file for mutual_recursion5.k
-- Contains the other half of a mutual recursion pair
-- NOTE: Not runnable standalone - requires mutual_recursion5a.k

package mutual_test

import mutual_test.mutual_recursion5a

fun helperB(n: Int) : Int {
  if n <= 0 then 0
  else 1 + helperA(n - 1)
}
