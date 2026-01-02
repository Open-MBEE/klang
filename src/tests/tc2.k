-- Function redefinition not allowed when parent has a body
-- (child declares without body, but parent already has implementation)
-- @expected ERROR
class A {
  fun foo(x : Int) : Int { x + 1 } 
}

class B extends A { 
  fun foo(x : Int) : Int
}