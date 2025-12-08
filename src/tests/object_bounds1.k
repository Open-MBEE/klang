-- Test: object_bounds1.k
-- Tests basic object creation
-- Expected: PASSED

class Item {
  value : Int
  req value > 0
  req value < 100
}

class Container {
  item1 : Item
  item2 : Item

  -- Items must be different
  req item1.value != item2.value
}

container : Container

