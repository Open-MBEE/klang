-- Test that field shadowing WITHOUT rename clause produces an error
-- should not type check

class Parent {
  value : Int
}

class Child extends Parent { 
  value : Int  -- ERROR: field shadows parent's field
}
