-- Test rename declaration for keeping both parent and child fields
-- This should compile and solve successfully

class Parent {
  value : Int
}

class Child extends Parent {
  rename Parent::value as parentValue;
  value : Int
}

c : Child

req c.value > 0
req c.parentValue > 0
req c.value = 42
req c.parentValue = 100
