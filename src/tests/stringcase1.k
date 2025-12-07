// Test: String case conversion - toUpper, toLower
// Expected: SAT with correct case conversions

class StringCase1 {
  mixed : String
  req mixed = "Hello World"

  // Test toUpper
  upper : String
  req upper = mixed.toUpper()
  req upper = "HELLO WORLD"

  // Test toLower
  lower : String
  req lower = mixed.toLower()
  req lower = "hello world"

  // Test with already-converted string
  allCaps : String
  req allCaps = "ABC"

  capsToLower : String
  req capsToLower = allCaps.toLower()
  req capsToLower = "abc"
}

main : StringCase1

