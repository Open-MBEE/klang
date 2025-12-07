// Test: Solver finds string values matching regex constraints
// This is the REAL power of regex + SMT: finding unknown values
// Expected: SAT - solver finds strings satisfying regex + other constraints

class RegexSolver1 {
  // Find a 3-digit number
  number : String
  req number.matches("[0-9]{3}")
  req number.length = 3

  // Find a lowercase word of length 4
  word : String
  req word.matches("[a-z]+")
  req word.length = 4

  // Find a string starting with "test" followed by digits
  testId : String
  req testId.matches("test[0-9]+")
  req testId.startsWith("test")

  // Find a valid identifier: starts with letter, then alphanumeric
  identifier : String
  req identifier.matches("[a-z][a-z0-9]*")
  req identifier.length >= 3
  req identifier.length <= 6
}

main : RegexSolver1

