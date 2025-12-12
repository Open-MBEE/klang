// Minimal test for unsigned int conversion
class UnsignedIntMinimal {
  val1 : Int = 200
  u8  : UInt8  = val1 as UInt8
  back8  : Int = u8 as Int
  req back8 = 200
}

main : UnsignedIntMinimal
