-- Test diamond inheritance resolved with rename (keep both copies)
-- Unlike share (single instance), rename keeps distinct fields from each path

class D {
  x : Int
}

class B extends D {
  bVal : Int
}

class C extends D {
  cVal : Int
}

-- A extends B and C, both have D::x
-- Use rename to keep both as distinct fields
class A extends B, C {
  rename B::x as bx;
  rename C::x as cx;
  aVal : Int
}

a : A

req a.bx > 0
req a.cx > 0
req a.bVal > 0
req a.cVal > 0
req a.aVal > 0

-- They can have different values (unlike share which would have one value)
req a.bx = 10
req a.cx = 20
