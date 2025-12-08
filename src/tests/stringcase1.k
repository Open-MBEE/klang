// Test: String case - basic string comparisons
// Note: toUpper/toLower require Z3 4.12+ which may not be fully supported
// Expected: SAT with string equality constraints

class StringCase1 {
  text1 : String
  text2 : String

  req text1 = "Hello"
  req text2 = "Hello"

  // Test equality
  areEqual : Bool
  req areEqual = (text1 = text2)
  req areEqual = true

  // Different strings
  text3 : String
  req text3 = "World"

  areDifferent : Bool
  req areDifferent = (text1 != text3)
  req areDifferent = true
}

main : StringCase1
