-- Abstract function in parent can be implemented in child
-- (parent declares without body, child provides implementation)
-- @expected SAT
class A {
  fun foo(x : Int) : Int
}

class B extends A { 
  fun foo(x : Int) : Int { x + 2 }
}