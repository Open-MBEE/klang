-- Test diamond inheritance with share clause
-- This should compile and solve successfully

class Base {
  baseVal : Int
}

class Left extends Base {
  leftVal : Int
}

class Right extends Base {
  rightVal : Int
}

-- Without 'share Base', this would error due to diamond inheritance
class Diamond extends Left, Right {
  share Base;
  diamondVal : Int
}

d : Diamond

-- All fields should be accessible
req d.baseVal > 0
req d.leftVal > 0
req d.rightVal > 0
req d.diamondVal > 0

-- baseVal should have a single consistent value
req d.baseVal = 10
