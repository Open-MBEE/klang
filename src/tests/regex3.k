// Test: Regex pattern that should NOT match
// Expected: SAT - strings don't match the wrong patterns

class RegexNoMatch1 {
  // Text should NOT match digit pattern
  text : String
  req text = "hello"

  matchesDigits : Bool
  req matchesDigits = text.matches("[0-9]+")
  req matchesDigits = false

  // Number should NOT match letter pattern
  number : String
  req number = "12345"

  matchesLetters : Bool
  req matchesLetters = number.matches("[a-z]+")
  req matchesLetters = false
}

main : RegexNoMatch1

