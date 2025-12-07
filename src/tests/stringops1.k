// Test: String operations - length, substring, charAt
// Expected: SAT with all string operations giving expected values

class StringOps1 {
  text : String
  req text = "ABCDEF"
  
  // Test length
  len : Int
  req len = text.length
  req len = 6
  
  // Test substring
  sub1 : String
  req sub1 = text.substring(0, 3)
  req sub1 = "ABC"
  
  sub2 : String
  req sub2 = text.substring(2, 5)
  req sub2 = "CDE"
  
  // Test charAt
  ch0 : String
  req ch0 = text.charAt(0)
  req ch0 = "A"
  
  ch3 : String
  req ch3 = text.charAt(3)
  req ch3 = "D"
}

main : StringOps1
