-- Test deep diamond: A extends B, C; B extends D, E; C extends D, E
-- This creates two diamonds at once

class D {
  dx : Int
}

class E {
  ex : Int
}

class B extends D, E {
  bx : Int
}

class C extends D, E {
  cx : Int
}

-- A has diamond through both D and E
class A extends B, C {
  share D;
  share E;
  ax : Int
}

a : A

req a.dx = 1   -- Shared from D
req a.ex = 2   -- Shared from E
req a.bx = 3   -- From B
req a.cx = 4   -- From C
req a.ax = 5   -- Own field
