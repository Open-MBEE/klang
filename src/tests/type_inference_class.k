// Test type inference in K with class instances
// Properties with initialization expressions should have their types inferred

class A {
    x : Int = 5
}

// Top-level property with explicit type
a : A

// Type inference for property initialized from class member access
ax = a.x
