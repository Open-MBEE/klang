-- Test that rename only applies to fields, not methods
-- Methods are inherited normally without rename

class Parent {
  x : Int
  
  fun getValue() : Int {
    return x
  }
}

class Child extends Parent {
  rename Parent::x as parentX;
  x : Int  -- New field
}

c : Child

req c.parentX = 10
req c.x = 20

-- getValue should still work - it accesses parentX (the renamed field)
