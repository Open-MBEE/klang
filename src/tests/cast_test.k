// Basic test for numeric type conversions
// Tests basic Int/Real/BitVec conversions that are currently supported

// ============================================================
// TEST 1: Int -> Real widening (implicit)
// ============================================================
class IntToRealWidening {
  x : Int = 42
  y : Real = 3.5

  // Int should be usable where Real is expected (widening)
  req x >= 0
  req y > 0.0

  // Mixed comparison: x implicitly widens to Real
  req x < y * 20.0
}

// ============================================================
// TEST 2: Real -> Int narrowing (explicit cast)
// ============================================================
class RealToIntCast {
  pi : Real = 3.14159
  truncated : Int = pi as Int

  // Truncation should give floor value
  req truncated = 3

  // Negative truncation
  negReal : Real = -2.7
  negTrunc : Int = negReal as Int
  req negTrunc = -3  // Should truncate toward zero
}

// ============================================================
// TEST 3: Int <-> BitVec conversions (explicit cast)
// ============================================================
class BitVecConversions {
  intVal : Int = 255
  bv : BitVec[8] = intVal as BitVec[8]
  backToInt : Int = bv as Int

  req backToInt = 255
}

// Note: SignedIntType (Int8, Int16, etc.), UnsignedIntType (UInt8, etc.),
// and width conversions require additional SMT support for toSMT generation.
// These are tested in cast_advanced_test.k when fully implemented.
