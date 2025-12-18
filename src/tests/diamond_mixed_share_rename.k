-- Test mixed share and rename: share one diamond ancestor, rename fields from another path

class D1 {
  x : Int
}

class D2 {
  y : Int
}

class B extends D1, D2 {
}

class C extends D1, D2 {
}

-- A extends B and C, creating two diamonds (D1 and D2)
-- Share D1 (single x), rename D2::y from each path
class A extends B, C {
  share D1;
  rename B::y as by;
  rename C::y as cy;
}

a : A

req a.x = 10      -- Single shared x from D1
req a.by = 20     -- y from B's path
req a.cy = 30     -- y from C's path (different value)
