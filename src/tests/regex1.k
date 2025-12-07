// Test: String regex matching with matches()
// Expected: SAT - strings match their regex patterns

class RegexTest1 {
  // Simple digit pattern
  digit : String
  req digit = "5"
  req digit.matches("[0-9]")

  // Multiple digits
  number : String
  req number = "123"
  req number.matches("[0-9]+")

  // Simple word
  word : String
  req word = "hello"
  req word.matches("[a-z]+")
}

main : RegexTest1
