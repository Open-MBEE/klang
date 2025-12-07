// Test: String operations - startsWith, endsWith, contains
// Expected: SAT with all string predicates satisfied

class StringPred1 {
  text : String
  prefix : String
  suffix : String
  middle : String
  
  req text = "Hello World"
  req prefix = "Hello"
  req suffix = "World"
  req middle = "lo Wo"
  
  // Test startsWith
  hasPrefix : Bool
  req hasPrefix = text.startsWith(prefix)
  req hasPrefix = true
  
  // Test endsWith
  hasSuffix : Bool
  req hasSuffix = text.endsWith(suffix)
  req hasSuffix = true
  
  // Test contains
  hasMiddle : Bool
  req hasMiddle = text.contains(middle)
  req hasMiddle = true
}

main : StringPred1
