// String Operations Demo
// Demonstrates K string capabilities with Z3 solver
// Features: concatenation, length, predicates, substring, indexOf

package examples.StringDemo

// Demonstrates string concatenation
class Greeting {
  hello : String
  name : String
  message : String

  req hello = "Hello"
  req name = "World"

  // String concatenation with +
  req message = hello + ", " + name + "!"
}

// Demonstrates length and predicates
class EmailValidator {
  email : String
  isValid : Bool

  req email = "user@example.com"

  // Length check
  req email.length >= 5
  req email.length <= 50

  // Must contain @
  req email.contains("@")

  // Must end with valid domain
  req email.endsWith(".com")

  req isValid = true
}

// Demonstrates startsWith, endsWith
class FileName {
  name : String
  extension : String

  req name = "document.pdf"

  // Check file properties
  req name.startsWith("doc")
  req name.endsWith(".pdf")
  req name.contains(".")

  req extension = "pdf"
}

// Demonstrates substring and indexOf
class StringParsing {
  text : String
  firstWord : String
  commaPos : Int

  req text = "Hello, World"

  // Extract first word using substring
  req firstWord = text.substring(0, 5)

  // Find position of comma
  req commaPos = text.indexOf(",")
}

// Top-level instances
greeting : Greeting
emailValidator : EmailValidator
fileName : FileName
stringParsing : StringParsing


