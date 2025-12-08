// Type inference with inheritance
// Tests that type inference works correctly with class hierarchies

class Animal {
    age : Int = 0
    name : String = "unknown"
}

class Dog extends Animal {
    breed : String = "mixed"

    // Infer from own class members only
    dogBreed = breed
}

class Cat extends Animal {
    indoor : Bool = true

    // Infer from own members
    isIndoor = indoor
}

// Top-level instances with explicit types
dog : Dog
cat : Cat

// Type inference from own class members (not inherited)
inferredBreed = dog.breed
inferredIndoor = cat.indoor

// Constraints using explicit member access
req dog.breed = "mixed"
req cat.indoor = true
