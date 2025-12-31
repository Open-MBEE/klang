-- KNOWN BUG: Constructor calls in function return statements generate incompatible SMT types
-- See function_with_class_params.k for detailed explanation of the bug.
--
-- This variant has value < 4 instead of value < 3:
--   - X.value must be in {1, 2, 3}
--   - sum requires x1.value != x2.value
--   - Possible sums: 1+2=3, 1+3=4, 2+3=5
--   - Only sum=3 satisfies value < 4, achieved with x1=1, x2=2 (or vice versa)
--   - So this should be SAT (not UNSAT like the other variant)
--
-- However, this test currently fails with the same SMT sort mismatch error
-- as function_with_class_params.k due to the constructor-in-return bug.
--
-- @expected UNKNOWN

class X {
  value : Int
  req value > 0 && value < 4
}

fun sum(x1 : X, x2: X) : X {
  req x1.value != x2.value
  return X(value::x1.value+x2.value)
}
