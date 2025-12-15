-- Test shadow declaration for intentionally hiding parent's field
-- This should compile and solve successfully

class Parent {
  value : Int
}

class Child extends Parent {
  shadow Int value;  -- Hides parent's value and declares child's own
}

c : Child

req c.value > 0
req c.value = 42
