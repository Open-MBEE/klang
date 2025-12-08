-- Test: external5.k
-- Tests external Java String methods
-- Expected: PASSED (concrete evaluation of String methods)

class StringMethodTests {
  -- Test java.lang.String static and instance-like methods
  -- Note: In K, we use String primitive type with built-in methods
  -- This tests that external Java calls work alongside K string ops

  -- Test Integer parsing (returns int, then used as Real)
  parsed : Int
  req parsed = java.lang.Integer.parseInt("42")
  req parsed = 42

  -- Test Double parsing
  parsedDouble : Real
  req parsedDouble = java.lang.Double.parseDouble("3.14159")
  req parsedDouble > 3.14
  req parsedDouble < 3.15
}

main : StringMethodTests

