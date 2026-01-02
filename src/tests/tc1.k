-- Property redefinition in subclass is not allowed
-- @expected ERROR
class A {
  x : Int
}

class B extends A { 
  x : Int
}