-- Test: array_types.k
-- Tests Java-style array type syntax (Type[])
-- Note: Array syntax only works for class types, not primitives
-- For primitives, use Seq[Int] syntax
-- @expected SAT

class Item {
  value : Int
}

class Container {
  -- Class array types: Item[] becomes Seq[Item]
  items : Item[]

  -- For primitives, use Seq syntax explicitly
  numbers : Seq[Int]

  -- Constraints using Seq operations
  req numbers.size >= 1
  req numbers.at(0) = 42
}

c : Container
