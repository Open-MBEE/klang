-- Test diamond inheritance with multiple shared ancestors
-- More complex diamond pattern

class A {
  aVal : Int
}

class B {
  bVal : Int  
}

class C extends A, B {
  cVal : Int
}

class D extends A, B {
  dVal : Int
}

-- Both A and B are shared ancestors
class E extends C, D {
  share A, B;
  eVal : Int
}

e : E

req e.aVal > 0
req e.bVal > 0
req e.cVal > 0
req e.dVal > 0
req e.eVal > 0
