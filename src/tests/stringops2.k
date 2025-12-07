// Test: String operations - indexOf, replace
// Expected: SAT with correct indexOf and replace results

class StringOps2 {
  text : String
  req text = "hello world hello"
  
  // Test indexOf
  idx1 : Int
  req idx1 = text.indexOf("world")
  req idx1 = 6
  
  idx2 : Int
  req idx2 = text.indexOf("hello")
  req idx2 = 0  // First occurrence
  
  idx3 : Int
  req idx3 = text.indexOf("xyz")
  req idx3 = -1  // Not found
  
  // Test replace (replaces first occurrence)
  replaced : String
  req replaced = text.replace("hello", "hi")
  req replaced = "hi world hello"
}

main : StringOps2
