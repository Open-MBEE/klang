// @expected SAT
// Test: subclass inherits parent objective; both minimize (expect a=3, b=5)
class OptP { a : Int req a >= 3 minimize a }
class OptC extends OptP { b : Int req b >= a + 2 minimize b }
c : OptC
