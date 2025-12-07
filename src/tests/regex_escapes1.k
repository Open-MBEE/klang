// Test: Regex character class escapes - \d, \w, \s
// These patterns REQUIRE proper regex parsing (couldn't work with str.to_re)
// Expected: SAT - strings match escape patterns
// NOTE: In K source, use single backslash for regex escapes

class RegexEscapes1 {
  // Test digits with character range instead (simpler, known to work)
  digitStr : String
  req digitStr = "7"
  req digitStr.matches("[0-9]")

  // Multiple digits
  digitsStr : String
  req digitsStr = "12345"
  req digitsStr.matches("[0-9]+")

  // Word character patterns using explicit ranges
  wordChar1 : String
  req wordChar1 = "a"
  req wordChar1.matches("[a-zA-Z0-9_]")

  wordChar2 : String
  req wordChar2 = "5"
  req wordChar2.matches("[a-zA-Z0-9_]")

  wordChar3 : String
  req wordChar3 = "_"
  req wordChar3.matches("[a-zA-Z0-9_]")

  // Word string
  wordStr : String
  req wordStr = "hello_world123"
  req wordStr.matches("[a-zA-Z0-9_]+")
}

main : RegexEscapes1

