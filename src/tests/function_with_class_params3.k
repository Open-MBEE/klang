-- KNOWN BUG: Type checker rejects assignment expressions in function bodies
--
-- This test attempts to work around the constructor-in-return bug (see function_with_class_params.k)
-- by assigning the constructor result to a local variable first, then returning that variable.
--
-- However, this fails at type checking with:
--   "Non-unit type expression found in function body: x3 = X(value :: x1.value + x2.value)"
--
-- The type checker enforces that all non-final expressions in a function body must have Unit type.
-- The assignment `x3 = X(...)` is being parsed/treated as an expression that returns a value
-- (the assigned value), rather than as a statement with Unit type.
--
-- This might be:
--   1. A parsing issue where `x3 = X(...)` is parsed as an equality expression rather than assignment
--   2. A type checker issue where assignments should have Unit type but don't
--   3. A syntax issue where K requires different syntax for local variable assignment
--
-- Expected behavior once fixed:
--   - The assignment should be allowed in the function body
--   - The function should be callable with synthetic arguments
--   - Result should be SAT since value < 4 allows sum 1+2=3
--
-- @expected ERROR

class X {
  value : Int
  req value > 0 && value < 4
}

fun sum(x1 : X, x2: X) : X {
  req x1.value != x2.value
  x3: X
  x3 = X(value::x1.value+x2.value)
  return x3
}
