// Test: String case operations - stringcase1 already exists
// This tests edge cases: empty string, single char

class StringEdgeCases1 {
  // Empty string length
  empty : String
  req empty = ""

  emptyLen : Int
  req emptyLen = empty.length
  req emptyLen = 0

  // Single character
  single : String
  req single = "X"

  singleLen : Int
  req singleLen = single.length
  req singleLen = 1

  // Single char charAt
  singleChar : String
  req singleChar = single.charAt(0)
  req singleChar = "X"

  // Contains empty string (should be true)
  containsEmpty : Bool
  req containsEmpty = single.contains("")
  req containsEmpty = true

  // StartsWith empty string (should be true)
  startsWithEmpty : Bool
  req startsWithEmpty = single.startsWith("")
  req startsWithEmpty = true
}

main : StringEdgeCases1

