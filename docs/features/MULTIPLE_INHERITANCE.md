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

**Current behavior**: Compile error - `"x declared multiple times. Use 'shadow x;' or 'rename ...::x as newName;' to resolve."`

**Resolution mechanisms**:

#### Using `shadow` (intentionally hide parent's field):
```k
class B extends A {
  shadow Int x;  // OK: parent's x is intentionally hidden
}
// b.x is B's own field, parent's x is inaccessible
```

#### Using `rename` (keep both fields with distinct names):
```k
class B extends A {
  rename A::x as parentX;
  x : Int;  // OK: parent's x is now accessible as parentX
}
// b.x and b.parentX both exist
```

### 2. Diamond Problem

**Situation**: Class extends multiple parents that share a common ancestor with a field.

```
        D (x : Int)
       / \
      B   C 
       \ /
        A        // ERROR: x inherited multiple times from D
```

```k
class D { x : Int }
class B extends D { }
class C extends D { }
class A extends B, C { }  // ERROR: Diamond inheritance
```

**Current behavior**: Compile error - `"x inherited multiple times from D (diamond inheritance). Use 'share D;' to resolve."`

**Resolution mechanisms**:

#### Using `share` (single instance, like virtual inheritance):
```k
class A extends B, C {
  share D;
}
// a.x is a single shared field from D
```

#### Using `rename` (keep both with distinct names):
```k
class A extends B, C {
  rename B::x as bx;
  rename C::x as cx;
}
// a.bx and a.cx both exist (from B's D and C's D)
```

## Syntax

### Share Declaration

Used inside a class body to resolve diamond inheritance by specifying that the ancestor's fields should be shared (single instance):

```k
class Child extends Parent1, Parent2 {
  share SharedAncestor;
  // ...
}

// Multiple shared ancestors
class Child extends A, B, C {
  share X, Y;
  // ...
}
```

### Rename Declaration

Used inside a class body to resolve field name conflicts by renaming inherited fields:

```k
class Child extends Parent {
  rename Parent::fieldName as newName;
  // ...
}
```

### Shadow Declaration

Used inside a class body to intentionally hide a parent's field:

```k
class Child extends Parent {
  shadow Int fieldName;
  // ...
}
```

## Design Philosophy

> "Compiler doesn't guess, programmer declares intent."

- `rename` = "there's a name collision but both fields are meaningful to me"
- `shadow` = "I'm replacing this concept entirely in my subclass"
- `share` = "there's one ancestor, one set of fields" (virtual inheritance)

## Test Cases

- `src/tests/diamond_share.k` - Diamond with `share` clause
- `src/tests/diamond_share_multiple.k` - Diamond with multiple shared ancestors
- `src/tests/diamond_error.k` - Error when `share` is missing
- `src/tests/tc1.k` - Field shadowing error
- `src/tests/field_shadow_error.k` - Field shadowing error

## Related Files

- `src/k/frontend/TypeChecker.scala` - `union2WithModifiers` handles share/rename/shadow
- `src/k/frontend/AbstractSyntax.scala` - `ShareDecl`, `RenameDecl`, `ShadowDecl` types
- `src/grammar/Model.g4` - Grammar for `share`, `rename`, `shadow` declarations
