// Comprehensive numeric type tests for K language
// Tests all new numeric types: Int8-64, UInt8-64, Float32/64
// And type conversions with the `as` operator

// ============================================================
// TEST 1: Signed Integer Types - Basic declarations and casts
// ============================================================
class SignedIntBasics {
  // Int to signed int conversions
  val1 : Int = 42
  i8  : Int8  = val1 as Int8
  i16 : Int16 = val1 as Int16
  i32 : Int32 = val1 as Int32
  i64 : Int64 = val1 as Int64
  
  // Back to Int
  back8  : Int = i8 as Int
  back16 : Int = i16 as Int
  back32 : Int = i32 as Int
  back64 : Int = i64 as Int
  
  // Verify round-trip
  req back8 = 42
  req back16 = 42
  req back32 = 42
  req back64 = 42
}

// ============================================================
// TEST 2: Unsigned Integer Types - Basic declarations and casts
// ============================================================
class UnsignedIntBasics {
  val1 : Int = 200
  u8  : UInt8  = val1 as UInt8
  u16 : UInt16 = val1 as UInt16
  u32 : UInt32 = val1 as UInt32
  u64 : UInt64 = val1 as UInt64
  
  // Back to Int (using unsigned semantics)
  back8  : Int = u8 as Int
  back16 : Int = u16 as Int
  back32 : Int = u32 as Int
  back64 : Int = u64 as Int
  
  // Verify round-trip
  req back8 = 200
  req back16 = 200
  req back32 = 200
  req back64 = 200
}

// ============================================================
// TEST 3: Signed Integer Overflow (wrapping behavior)
// ============================================================
class SignedOverflow {
  // Int8 range is -128 to 127
  maxInt8 : Int = 127
  i8max : Int8 = maxInt8 as Int8
  backMax : Int = i8max as Int
  req backMax = 127
  
  // 128 should wrap to -128 in Int8
  overflow : Int = 128
  i8over : Int8 = overflow as Int8
  backOver : Int = i8over as Int
  req backOver = -128
  
  // -129 should wrap to 127 in Int8  
  underflow : Int = -129
  i8under : Int8 = underflow as Int8
  backUnder : Int = i8under as Int
  req backUnder = 127
}

// ============================================================
// TEST 4: Unsigned Integer Overflow (wrapping behavior)
// ============================================================
class UnsignedOverflow {
  // UInt8 range is 0 to 255
  maxUInt8 : Int = 255
  u8max : UInt8 = maxUInt8 as UInt8
  backMax : Int = u8max as Int
  req backMax = 255
  
  // 256 should wrap to 0 in UInt8
  overflow : Int = 256
  u8over : UInt8 = overflow as UInt8
  backOver : Int = u8over as Int
  req backOver = 0
  
  // 257 should wrap to 1 in UInt8
  overflow2 : Int = 257
  u8over2 : UInt8 = overflow2 as UInt8
  backOver2 : Int = u8over2 as Int
  req backOver2 = 1
}

// ============================================================
// TEST 5: Negative numbers in unsigned types
// ============================================================
class NegativeToUnsigned {
  neg : Int = -1
  // -1 as UInt8 should be 255 (two's complement)
  u8neg : UInt8 = neg as UInt8
  backNeg : Int = u8neg as Int
  req backNeg = 255
  
  neg2 : Int = -10
  // -10 as UInt8 should be 246
  u8neg2 : UInt8 = neg2 as UInt8
  backNeg2 : Int = u8neg2 as Int
  req backNeg2 = 246
}

// ============================================================
// TEST 6: Float32 and Float64 conversions
// ============================================================
class FloatBasics {
  // Int to Float
  intVal : Int = 42
  f32 : Float32 = intVal as Float32
  f64 : Float64 = intVal as Float64
  
  // Float to Int (truncation)
  pi : Real = 3.14159
  f32pi : Float32 = pi as Float32
  f64pi : Float64 = pi as Float64
  
  // Float to Real
  realFromF32 : Real = f32pi as Real
  realFromF64 : Real = f64pi as Real
  
  // Real value should be close to original
  req realFromF32 > 3.0
  req realFromF32 < 3.2
  req realFromF64 > 3.14
  req realFromF64 < 3.15
}

// ============================================================
// TEST 7: Float to Int truncation
// ============================================================
class FloatToIntTruncation {
  pos : Real = 3.7
  f32pos : Float32 = pos as Float32
  intPos : Int = f32pos as Int
  // Truncation toward zero
  req intPos = 3
  
  neg : Real = -3.7
  f64neg : Float64 = neg as Float64
  intNeg : Int = f64neg as Int
  // Truncation toward zero for negative
  req intNeg = -3
}

// ============================================================
// TEST 8: Hex literal to fixed-width types
// ============================================================
class HexLiterals {
  hexVal : Int = 0xFF
  u8hex : UInt8 = hexVal as UInt8
  backHex : Int = u8hex as Int
  req backHex = 255
  
  hexVal2 : Int = 0x1234
  u16hex : UInt16 = hexVal2 as UInt16
  backHex2 : Int = u16hex as Int
  req backHex2 = 4660
}

// ============================================================
// TEST 9: BitVec and Int conversions still work
// ============================================================
class BitVecIntCompat {
  val1 : Int = 255
  bv8 : BitVec[8] = val1 as BitVec[8]
  back : Int = bv8 as Int
  req back = 255
  
  // Larger value
  val2 : Int = 65535
  bv16 : BitVec[16] = val2 as BitVec[16]
  back2 : Int = bv16 as Int
  req back2 = 65535
}

// ============================================================
// TEST 10: Int16 boundary values
// ============================================================
class Int16Boundaries {
  // Int16 range: -32768 to 32767
  maxVal : Int = 32767
  i16max : Int16 = maxVal as Int16
  backMax : Int = i16max as Int
  req backMax = 32767
  
  minVal : Int = -32768
  i16min : Int16 = minVal as Int16
  backMin : Int = i16min as Int
  req backMin = -32768
  
  // Overflow test
  overMax : Int = 32768
  i16over : Int16 = overMax as Int16
  backOver : Int = i16over as Int
  req backOver = -32768
}

// ============================================================
// TEST 11: UInt16 boundary values
// ============================================================
class UInt16Boundaries {
  // UInt16 range: 0 to 65535
  maxVal : Int = 65535
  u16max : UInt16 = maxVal as UInt16
  backMax : Int = u16max as Int
  req backMax = 65535
  
  minVal : Int = 0
  u16min : UInt16 = minVal as UInt16
  backMin : Int = u16min as Int
  req backMin = 0
  
  // Overflow wraps
  overMax : Int = 65536
  u16over : UInt16 = overMax as UInt16
  backOver : Int = u16over as Int
  req backOver = 0
}

// Run all tests
main : SignedIntBasics
