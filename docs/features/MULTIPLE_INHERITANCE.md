# Multiple Inheritance Design Decisions

## Current Policy (Implemented)

K uses a **conservative approach** where the compiler doesn't guess programmer intent. Ambiguous situations result in compile errors.

### 1. Field Shadowing

**Situation**: Subclass declares a field with the same name as a field in a parent class.

```k
class A {
  x : Int
}

class B extends A { 
  x : Int  // ERROR: x declared multiple times
}
```

**Current behavior**: Compile error - `"x declared multiple times"`

**Future resolution mechanisms** (not yet implemented):
- `rename A::x as parentX` - Keep both fields with distinct names
- `shadow` keyword - Explicit opt-in when parent's field isn't needed

### 2. Diamond Problem

**Situation**: Class extends multiple parents that share a common ancestor with a field.

```
        F (freal : Real)
       / \
      D   E 
     /     \
    A       C
     \     /
       B        // ERROR: freal inherited multiple times
```

```k
// f.k
class F { freal : Real }

// e.k
class E extends F { eint : Int }

// d.k  
class D extends E { j : Bool }

// a.k
class A extends D { x : Int }

// c.k
class C extends D { z : Real }

// b.k
class B extends A, C { y : Int }  // ERROR
```

**Current behavior**: Compile error - `"freal declared multiple times"`

**Future resolution mechanisms** (not yet implemented):
- `share F` - Single instance of F's fields (like C++ virtual inheritance)
- `rename` - Keep both with distinct names

## Design Philosophy

> "Compiler doesn't guess, programmer declares intent."

Resolution mechanisms will be added based on real user pain points.

## Test Cases

- `src/tests/tc1.k` - Field shadowing error
- `src/examples/b.k` - Diamond problem error (depends on a.k, c.k, d.k, e.k, f.k)

## Related Files

- `src/k/frontend/TypeChecker.scala` - `TypeEnv.union2` detects duplicate fields (~line 242)
