// Test bitvector width conversions
// Tests sign_extend, zero_extend, and extract operations

// ========== Widening: Int8 -> Int16 (sign extend) ==========
class Int8ToInt16 {
  x: Int8 = 0xff
  y: Int16 = x as Int16
}

// ========== Widening: UInt8 -> UInt16 (zero extend) ==========
class UInt8ToUInt16 {
  x: UInt8 = 0xff
  y: UInt16 = x as UInt16
}

// ========== Narrowing: Int16 -> Int8 (extract) ==========
class Int16ToInt8 {
  x: Int16 = 0x0201
  y: Int8 = x as Int8
}

// ========== Narrowing: UInt16 -> UInt8 (extract) ==========
class UInt16ToUInt8 {
  x: UInt16 = 0xff80
  y: UInt8 = x as UInt8
}

// ========== Cross-sign widening: Int8 -> UInt16 ==========
class Int8ToUInt16 {
  x: Int8 = 0xff
  y: UInt16 = x as UInt16
}

// ========== Cross-sign widening: UInt8 -> Int16 ==========
class UInt8ToInt16 {
  x: UInt8 = 0xff
  y: Int16 = x as Int16
}

// ========== Cross-sign narrowing: Int16 -> UInt8 ==========
class Int16ToUInt8 {
  x: Int16 = 0xff00
  y: UInt8 = x as UInt8
}

// ========== Cross-sign narrowing: UInt16 -> Int8 ==========
class UInt16ToInt8 {
  x: UInt16 = 0x0080
  y: Int8 = x as Int8
}

// ========== Same width sign change: Int8 -> UInt8 ==========
class Int8ToUInt8 {
  x: Int8 = 0xff
  y: UInt8 = x as UInt8
}

// ========== Same width sign change: UInt8 -> Int8 ==========
class UInt8ToInt8 {
  x: UInt8 = 0x80
  y: Int8 = x as Int8
}

// ========== 32-bit conversions ==========
class Int32ToInt64 {
  x: Int32 = 0xffffffff
  y: Int64 = x as Int64
}

class UInt32ToUInt64 {
  x: UInt32 = 0xffffffff
  y: UInt64 = x as UInt64
}

// ========== Bitvector to Int conversion ==========
class SignedBvToInt {
  x: Int8 = 0xff
  y: Int = x as Int
}

class UnsignedBvToInt {
  x: UInt8 = 0xff
  y: Int = x as Int
}
