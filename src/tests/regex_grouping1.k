// Test: Regex grouping with () and optional with ?
// These patterns REQUIRE proper regex parsing
// Expected: SAT - strings match grouped/optional patterns

class RegexGrouping1 {
  // Grouped repetition: (ab)+
  repeated : String
  req repeated = "ababab"
  req repeated.matches("(ab)+")

  // Optional character: colou?r matches "color" or "colour"
  spelling1 : String
  req spelling1 = "color"
  req spelling1.matches("colou?r")

  spelling2 : String
  req spelling2 = "colour"
  req spelling2.matches("colou?r")

  // Simple optional group without special chars
  prefix1 : String
  req prefix1 = "test"
  req prefix1.matches("(un)?test")

  prefix2 : String
  req prefix2 = "untest"
  req prefix2.matches("(un)?test")
}

main : RegexGrouping1

