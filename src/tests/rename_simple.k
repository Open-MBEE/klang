-- Test simple rename without diamond - just renaming an inherited field

class Parent {
  value : Int
}

class Child extends Parent {
  rename Parent::value as parentValue;
  value : Int  -- New field with same name
}

c : Child

req c.parentValue = 100
req c.value = 200
req c.parentValue != c.value
