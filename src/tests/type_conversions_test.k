// Test file for numeric type conversions

class SimpleWideningTest {
  // ===== Basic Int -> Real widening =====
  x : Int = 42
  y : Real = 3.5

  // Int can be used in comparison with Real (widening)
  req x < y * 20.0
  req x >= 0
}

// Test type cast with as operator
class TypeCastTest {
  // Real -> Int truncation
  pi : Real = 3.14159
  truncated : Int = pi as Int

  req truncated = 3
}


