class StringOperationsTest {

  // String properties
  text : String
  prefix : String
  suffix : String

  // Derived properties using string operations
  textLength : Int
  hasPrefix : Bool
  hasSuffix : Bool
  firstChar : String
  middlePart : String

  // Concatenation properties
  greeting : String
  fullGreeting : String

  // Base constraints
  req text = "Hello World"
  req prefix = "Hello"
  req suffix = "World"

  // Length operation: str.len
  req textLength = text.length

  // Contains operations: str.prefixof, str.suffixof
  req hasPrefix = text.startsWith(prefix)
  req hasSuffix = text.endsWith(suffix)

  // Substring: str.substr
  req firstChar = text.substring(0, 1)
  req middlePart = text.substring(6, 11)

  // Concatenation: string + string
  req greeting = "Hi"
  req fullGreeting = greeting + ", " + suffix + "!"

}
