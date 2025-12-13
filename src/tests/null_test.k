-- Test null literal support
-- Basic test for null type checking - verifies the grammar and type checker work

-- Null with String: should work since String is a reference type
class StringTest {
  var s: String
  
  -- Function that checks if string is null
  fun isNull(): Bool {
    s = null
  }
  
  -- Function that checks if string is not null
  fun isNotNull(): Bool {
    s != null
  }
}

-- Null with class reference: should work  
class RefTest {
  var ref: StringTest
  
  fun isRefNull(): Bool {
    ref = null
  }
}

-- Test constraint with null - no invariants that conflict
class TestSimple {
  var holder: StringTest
  -- Just tests that we can parse and type-check null comparisons
  -- Even if result is UNSAT due to typing constraints, the syntax works
}

-- Expected: Type checking should succeed (SAT/UNSAT result depends on K's heap semantics)
