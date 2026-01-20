-- @expected UNSAT
-- Unsatisfiable: A.x > 10 conflicts with B.a.x < 1

class A {
  x : Int
 
  req x > 10 
}

class B {
  a : A
  
  req a.x < 1
}