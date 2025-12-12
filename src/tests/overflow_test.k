// Test signed overflow
class SignedOverflow {
  // 128 should wrap to -128 in Int8
  overflow : Int = 128
  i8over : Int8 = overflow as Int8
  backOver : Int = i8over as Int
  req backOver = -128
}

main : SignedOverflow
