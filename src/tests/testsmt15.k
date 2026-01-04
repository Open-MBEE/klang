
-- Test: testsmt15.k
-- Tests block expressions as values with arithmetic
-- Note: Parentheses required because = has higher precedence than +
-- @expected SAT

class A {
  RESULT_6 : Int

  req RESULT_6 = (
    { k1 : Int = 1
      k1
    }
    +
    { k2 : Int = 2
      k3 : Int = 3
      k2 + k3
    }
  )
}