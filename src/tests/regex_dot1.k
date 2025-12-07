// Test: Regex dot (.) matches any character
// This pattern REQUIRES proper regex parsing
// Expected: SAT - dot matches any single character

class RegexDot1 {
  // Single dot matches any char
  anyChar : String
  req anyChar = "x"
  req anyChar.matches(".")

  // Dot in pattern: a.c matches "abc", "axc", etc.
  threeChars1 : String
  req threeChars1 = "abc"
  req threeChars1.matches("a.c")

  threeChars2 : String
  req threeChars2 = "a5c"
  req threeChars2.matches("a.c")

  // Multiple dots: ... matches any 3 characters
  anyThree : String
  req anyThree = "xyz"
  req anyThree.matches("...")
}

main : RegexDot1

