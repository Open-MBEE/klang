-- Test that diamond inheritance auto-shares by default
-- Previously, this would produce an error without explicit share clause.
-- Now, diamond ancestors are automatically shared.
-- @expected SAT

class F {
  freal : Real
}

class D extends F {
  dval : Int
}

class E extends F {
  eval : Int
}

-- Previously would error. Now auto-shares F.
class B extends D, E {
  bval : Bool
}
