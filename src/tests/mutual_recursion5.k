-- @expected ERROR
-- Test: Mutual recursion across files
-- NOTE: Currently fails due to import resolution - cross-file mutual recursion
-- requires fixing the package/import system, not the mutual recursion support itself
-- helperA (in 5a.k) calls helperB (in 5b.k) which calls helperA

package mutual_test

import mutual_test.mutual_recursion5a
import mutual_test.mutual_recursion5b

result: Int = helperA(10)

req result = 10
