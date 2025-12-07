// Test: String/Int conversion - toInt, fromInt
// Expected: SAT with correct conversions between strings and integers

class StringInt1 {
  // String to Int
  numStr : String
  req numStr = "42"
  
  numVal : Int
  req numVal = numStr.toInt()
  req numVal = 42
  
  // Another string to int
  bigNumStr : String
  req bigNumStr = "12345"
  
  bigNumVal : Int
  req bigNumVal = bigNumStr.toInt()
  req bigNumVal = 12345
  
  // Negative number (if supported)
  // negStr : String
  // req negStr = "-7"
  // negVal : Int
  // req negVal = negStr.toInt()
  // req negVal = -7
}

main : StringInt1
