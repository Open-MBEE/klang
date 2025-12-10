-- Test: PropertyDecl interpreted as equality constraint when name is already in scope
-- When a variable name is already declared (e.g., as a class member or function parameter),
-- `name = expr` in a function body should be interpreted as an equality constraint (Bool)
-- rather than a new property declaration.

package test.property_as_constraint

class Angle {
  value : Int

  -- In this function, `value` is already declared as a class member.
  -- So `value = other.value` should be interpreted as `value == other.value` (Bool)
  -- rather than declaring a new local variable named `value`.
  fun eq(other: Angle) : Bool {
    value = other.value
  }

  req value >= 0 && value <= 360
}

class Event {
  t1 : Real
  t2 : Real

  -- Multiple equality constraints should work when combined with &&
  -- Note: parentheses needed to prevent grammar ambiguity with property declaration
  fun equal(e : Event) : Bool {
    (t1 = e.t1) && (t2 = e.t2)
  }

  -- Single equality constraint works without parentheses
  fun meets(e : Event) : Bool {
    t2 = e.t1
  }
}

-- Test instances
a1 : Angle
a2 : Angle
e1 : Event
e2 : Event

-- Test that the functions work as boolean constraints
req a1.eq(a2)
req e1.meets(e2)

