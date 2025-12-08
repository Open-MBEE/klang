// Test type inference in K
// Properties with initialization expressions should have their types inferred

class TypeInferenceTest {
  // Explicit types (should still work)
  explicitInt : Int = 42
  explicitString : String = "hello"
  explicitBool : Bool = true
  
  // Inferred types (new feature!)
  inferredInt = 100
  inferredString = "world"
  inferredBool = false
  inferredReal = 3.14
  
  // Inferred from expressions
  inferredSum = explicitInt + 10
  inferredConcat = explicitString + " there"
  
  // Constraints using inferred types
  req inferredInt > 50
  req inferredSum > 100
}
