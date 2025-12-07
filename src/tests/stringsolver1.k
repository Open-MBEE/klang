// Test: String constraints where solver finds values
// Expected: SAT - Z3 finds values satisfying string constraints

class StringSolver1 {
  // Let solver find a name that starts with "A" and ends with "e"
  name : String

  req name.length >= 3
  req name.length <= 6
  req name.startsWith("A")
  req name.endsWith("e")

  // Simple greeting using concatenation
  greeting : String
  req greeting = "Hi " + name
}

main : StringSolver1

