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

**Resolution mechanism**: Use `rename` to keep both fields with distinct names:

```k
class B extends A rename A::x as parentX {
  x : Int  // OK: parent's x is now accessible as parentX
}
```

### 2. Diamond Problem

**Situation**: Class extends multiple parents that share a common ancestor with a field.

```
        F (freal : Real)
       / \
      D   E 
       \ /
        B        // ERROR: freal inherited multiple times
```

```k
class F { freal : Real }
class D extends F { }
class E extends F { }
class B extends D, E { }  // ERROR: Diamond inheritance
```

**Current behavior**: Compile error - `"freal inherited multiple times from F (diamond inheritance). Use 'share F' to resolve."`

**Resolution mechanism**: Use `share` for virtual inheritance (single instance of shared fields):

```k
class B extends D, E share F {
  bval : Bool
}
```

With `share F`, the class B will have only one copy of F's fields, accessible through any path.

## Syntax

### Share Clause

Used to resolve diamond inheritance by specifying that the ancestor's fields should be shared (single instance):

```k
class Child extends Parent1, Parent2 share SharedAncestor {
  // ...
}

// Multiple shared ancestors
class Child extends A, B, C share X, Y {
  // ...
}
```

### Rename Clause

Used to resolve field name conflicts by renaming inherited fields:

```k
class Child extends Parent rename Parent::fieldName as newName {
  // ...
}
```

**Note**: The `rename` clause is parsed but full SMT generation support is not yet complete.

## Design Philosophy

> "Compiler doesn't guess, programmer declares intent."

The type checker explicitly detects diamond inheritance patterns and requires the programmer to specify how to resolve them.

## Test Cases

- `src/tests/tc1.k` - Field shadowing error
- `src/examples/b.k` - Diamond problem error (depends on a.k, c.k, d.k, e.k, f.k)

## Related Files

- `src/k/frontend/TypeChecker.scala` - `union2WithModifiers` handles share/rename
- `src/k/frontend/AbstractSyntax.scala` - `ShareClause`, `RenameClause` types
- `src/grammar/Model.g4` - Grammar for `share` and `rename` syntax
