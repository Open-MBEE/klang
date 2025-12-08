-- Test: object_bounds0.k
-- Test constraints affecting the number of class objects
-- Tests Seq[A] where A is a class with constraints
-- Expected: PASSED

class A {
  x : Int
  req x > 0
  req x < 3
}

a_arr : Seq[A]

req a_arr.size > 2 && a_arr.size < 10

-- All elements have x > 0 and x < 3 (enforced by class constraint)

req 10 < a_arr.collect(a -> a.x).sum()
