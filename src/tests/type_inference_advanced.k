// Advanced type inference tests
// Tests more complex scenarios for type inference

class Point {
    x : Int = 0
    y : Int = 0
}

class Rectangle {
    width : Int = 10
    height : Int = 20

    // Type inference within a class from other properties
    area = width * height
    perimeter = 2 * (width + height)
}

// Test inference from arithmetic expressions
sum = 1 + 2
product = 3 * 4
difference = 10 - 5

// Test inference from boolean expressions
isTrue = true
isFalse = false
andResult = isTrue && isFalse
orResult = isTrue || isFalse

// Test inference from comparison expressions
isGreater = 5 > 3
isEqual = 10 = 10

// Test inference from real literals
piValue = 3.14159
radius = 2.5
circumference = 2.0 * piValue * radius

// Test inference with class instances
p : Point
rect : Rectangle

// Inference from class member access
px = p.x
rectArea = rect.area

// Constraints to verify types work correctly
req sum > 0
req isGreater = true
req rectArea > 0

