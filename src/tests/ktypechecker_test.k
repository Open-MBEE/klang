-- K-based Type Checker Test
-- This test verifies the K-based type checker generates correct type constraints

-- Explicit types
x : Int = 10
y : Bool = true
z : String = "hello"

-- Type inference from literals
a = 42          -- Should infer Int
b = 3.14        -- Should infer Real  
c = "world"     -- Should infer String
d = false       -- Should infer Bool

-- Type inference from expressions
e = x + 5       -- Should infer Int (from x)
f = z + " there" -- Should infer String (from z)
g = a > 0       -- Should infer Bool (comparison result)

-- Constraints
req x > 0
req y || d
req e > a
