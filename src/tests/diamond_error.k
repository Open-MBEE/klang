-- Test that diamond inheritance WITHOUT share clause produces an error
-- should not type check

class F {
  freal : Real
}

class D extends F {
  dval : Int
}

class E extends F {
  eval : Int
}

-- Should error: Diamond inheritance without share
class B extends D, E {
  bval : Bool
}
