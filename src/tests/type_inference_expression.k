// Negative test: Type inference with incompatible types
// This should fail - cannot add Real and String
// @expected ERROR

x : Real
y : String
z : Real

req z = 0.5 + x + y
