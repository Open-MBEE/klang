-- Function redefinition not allowed when parent has a body
-- (both parent and child have bodies - conflicting implementations)
-- @expected ERROR
class A {
  fun foo(x : Int) : Int { x + 1 } 
}

class B extends A { 
  fun foo(x : Int) : Int { x + 2 }
}