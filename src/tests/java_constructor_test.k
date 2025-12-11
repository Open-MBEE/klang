-- Test: java_constructor_test.k
-- Tests calling Java static factory methods that create objects
-- Expected: PASSED

-- Note: Direct constructor calls like `BigDecimal("123")` are not yet supported
-- in the type checker. Use static factory methods instead.

class JavaFactoryTest {
  -- Test using static factory methods to create values
  -- BigInteger.valueOf returns a BigInteger from a long

  -- These are static method calls (work via ExternalFunctions)
  value : Int
  sqrtValue : Real

  req value = java.lang.Integer.valueOf(42)
  req sqrtValue = java.lang.Math.sqrt(100.0)

  -- Verify computed values
  req value = 42
  req sqrtValue = 10.0
}

main : JavaFactoryTest
