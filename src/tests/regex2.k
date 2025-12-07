// Test: More regex patterns - character classes, quantifiers
// Expected: SAT - strings match various regex patterns

class RegexTest2 {
  // Alphanumeric
  alphanum : String
  req alphanum = "abc123"
  req alphanum.matches("[a-z0-9]+")

  // Fixed length pattern - uppercase letters followed by digits
  code : String
  req code = "AB12"
  req code.matches("[A-Z]{2}[0-9]{2}")

  // Letter followed by digits
  id : String
  req id = "x42"
  req id.matches("[a-z][0-9]+")
}

main : RegexTest2

