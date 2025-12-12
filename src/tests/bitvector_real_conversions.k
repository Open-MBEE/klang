// Test bitvector to Real type conversions
// This tests both signed and unsigned bitvector conversions to Real numbers

// Signed Int8 to Real conversion
class SignedInt8ToReal {
  x: Int8 = 0x80  // -128 in signed interpretation
  y: Real = x as Real
}

class SignedInt8ToRealPositive {
  x: Int8 = 127  // Max positive Int8
  y: Real = x as Real
}

class SignedInt8ToRealZero {
  x: Int8 = 0
  y: Real = x as Real
}

// Unsigned UInt8 to Real conversion
class UnsignedInt8ToReal {
  x: UInt8 = 0x80  // 128 in unsigned interpretation
  y: Real = x as Real
}

class UnsignedInt8ToRealMax {
  x: UInt8 = 255  // Max UInt8
  y: Real = x as Real
}

// Larger bitvector sizes
class SignedInt16ToReal {
  x: Int16 = 0x8000  // -32768 in signed
  y: Real = x as Real
}

class UnsignedInt16ToReal {
  x: UInt16 = 0x8000  // 32768 in unsigned
  y: Real = x as Real
}

// Int32 conversions
class SignedInt32ToReal {
  x: Int32 = 0x80000000  // -2147483648 in signed
  y: Real = x as Real
}

class UnsignedInt32ToReal {
  x: UInt32 = 0x80000000  // 2147483648 in unsigned
  y: Real = x as Real
}

// Real to bitvector conversions
class RealToSignedInt8 {
  x: Real = 42.7  // Will truncate to 42
  y: Int8 = x as Int8
}

class RealToUnsignedInt8 {
  x: Real = 200.9  // Will truncate to 200
  y: UInt8 = x as UInt8
}

class RealToSignedInt16 {
  x: Real = 1000.0
  y: Int16 = x as Int16
}

class RealToUnsignedInt16 {
  x: Real = 60000.0
  y: UInt16 = x as UInt16
}
