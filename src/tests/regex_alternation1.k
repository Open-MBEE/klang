// Test: Regex alternation with |
// This pattern REQUIRES proper regex parsing (couldn't work with str.to_re)
// Expected: SAT - strings match alternation patterns

class RegexAlternation1 {
  // Simple alternation: cat or dog
  pet1 : String
  req pet1 = "cat"
  req pet1.matches("cat|dog")

  pet2 : String
  req pet2 = "dog"
  req pet2.matches("cat|dog")

  // Three-way alternation
  color1 : String
  req color1 = "red"
  req color1.matches("red|green|blue")

  color2 : String
  req color2 = "green"
  req color2.matches("red|green|blue")

  color3 : String
  req color3 = "blue"
  req color3.matches("red|green|blue")
}

main : RegexAlternation1

