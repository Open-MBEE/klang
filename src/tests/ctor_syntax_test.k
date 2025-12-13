-- Test: ctor_syntax_test.k
-- Tests constructor application syntax for built-in types
-- Expected: PASSED
-- This tests the CtorApplExp type-checking and SMT generation

-- Test 1: User-defined class with 'new' syntax
class Point {
  x : Int
  y : Int
}

class TestUserDefinedCtor {
  -- Create Point using 'new' constructor syntax
  p : Point = new Point(10, 20)
  
  -- Verify the values are assigned correctly
  req p.x = 10
  req p.y = 20
}

-- Test 2: Seq with constructor syntax
class TestSeqCtor {
  -- Seq literal using constructor syntax
  s : Seq[Int] = Seq[Int](1, 2, 3)
  
  -- Verify sequence contents
  req s.size = 3
  req s.at(0) = 1
  req s.at(1) = 2
  req s.at(2) = 3
}

-- Test 3: Nested class constructors  
class Rectangle {
  topLeft : Point
  bottomRight : Point
}

class TestNestedCtor {
  r : Rectangle
  
  -- Assign points via constraints
  req r.topLeft.x = 0
  req r.topLeft.y = 0
  req r.bottomRight.x = 100
  req r.bottomRight.y = 50
}
