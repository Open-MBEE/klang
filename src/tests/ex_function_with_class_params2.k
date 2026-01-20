-- Test: Function returning constructor call (variant with wider bounds)
--
-- Similar to function_with_class_params.k but with value < 4 instead of value < 3.
-- The function is defined but not called, so SAT.
--
-- @expected SAT

class X {
  value : Int
  req value > 0 && value < 4
}

fun sum(x1 : X, x2: X) : X {
  req x1.value != x2.value
  return X(value::x1.value+x2.value)
}
