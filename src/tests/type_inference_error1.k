// Negative test: Type inference errors
// This file should FAIL type checking - properties without types or initializers
// @expected ERROR

class BadClass {
    // This should fail - no type and no initializer
    missingBoth
}

