// Regression test for signed/unsigned integer type conversions
// Tests that Int8/Int16 (signed) use signed interpretation 
// and UInt8/UInt16 (unsigned) use unsigned interpretation

// ============================================================
// TEST 1: Signed Int8 -> Int (value 128 = -128 signed)
// ============================================================
class SignedInt8ToInt {
  mid: Int8 = 128
  mid_int: Int = mid as Int
  req mid_int = -128
}

// ============================================================
// TEST 2: Signed Int8 -> Int (value 255 = -1 signed)
// ============================================================
class SignedInt8MaxToInt {
  max: Int8 = 255
  max_int: Int = max as Int
  req max_int = -1
}

// ============================================================
// TEST 3: Unsigned UInt8 -> Int (value 128 = 128 unsigned)
// ============================================================
class UnsignedUInt8ToInt {
  mid: UInt8 = 128
  mid_int: Int = mid as Int
  req mid_int = 128
}

// ============================================================
// TEST 4: Unsigned UInt8 -> Int (value 255 = 255 unsigned)
// ============================================================
class UnsignedUInt8MaxToInt {
  max: UInt8 = 255
  max_int: Int = max as Int
  req max_int = 255
}

// ============================================================
// TEST 5: Signed Int16 -> Int (value 32768 = -32768 signed)
// ============================================================
class SignedInt16ToInt {
  mid: Int16 = 32768
  mid_int: Int = mid as Int
  req mid_int = -32768
}

// ============================================================
// TEST 6: Unsigned UInt16 -> Int (value 32768 = 32768 unsigned)
// ============================================================
class UnsignedUInt16ToInt {
  mid: UInt16 = 32768
  mid_int: Int = mid as Int
  req mid_int = 32768
}
