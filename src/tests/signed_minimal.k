// Minimal test for signed int conversion
class SignedIntMinimal {
  val1 : Int = 42
  i8  : Int8  = val1 as Int8
  back8  : Int = i8 as Int
  req back8 = 42
}

main : SignedIntMinimal
