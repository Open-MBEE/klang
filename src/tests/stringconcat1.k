// Test: String concatenation with +
// Expected: SAT with correct string concatenation

class StringConcat1 {
  first : String
  second : String
  combined : String
  
  req first = "Hello"
  req second = "World"
  req combined = first + " " + second
  req combined = "Hello World"
  
  // Build a path
  dir : String
  file : String
  path : String
  
  req dir = "/home/user"
  req file = "document.txt"
  req path = dir + "/" + file
  req path = "/home/user/document.txt"
}

main : StringConcat1
