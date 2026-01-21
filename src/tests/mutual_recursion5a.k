-- @expected ERROR
-- Helper file for mutual_recursion5.k
-- Contains one half of a mutual recursion pair
-- NOTE: Not runnable standalone - requires mutual_recursion5b.k

package mutual_test

import mutual_test.mutual_recursion5b

fun helperA(n: Int) : Int {
  if n <= 0 then 0
  else 1 + helperB(n - 1)
}
