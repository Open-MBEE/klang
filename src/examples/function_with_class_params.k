-- KNOWN BUG: Constructor calls in function return statements generate incompatible SMT types
--
-- Problem Description:
-- When a function returns a class type and the return statement contains a constructor call,
-- the SMT generation produces type-incompatible code:
--
--   1. The function is declared with return type `Ref` (which is an alias for `Int`)
--   2. But the constructor call `X(value::...)` generates `(lift-X (mk-X ...))`
--   3. `lift-X` wraps an X value into the `Any` datatype (used for the polymorphic heap)
--   4. So the function body returns `Any` but the signature says `Ref` → sort mismatch
--
-- Generated SMT (problematic):
--   (define-fun TopLevelDeclarations.sum ((this Ref)(x1 Ref)(x2 Ref)) Ref
--     (lift-X (mk-X (+ (X.value x1) (X.value x2)))))  ; Returns Any, not Ref!
--
-- The issue is in AbstractSyntax.scala around line 3695 where constructor applications
-- generate `(lift-$ident (mk-$ident $argsSMT))`. For heap allocation this is correct,
-- but for function returns we need to either:
--   a) Change function return types to `Any` when returning class types, OR
--   b) Have constructor returns allocate on heap and return a Ref, OR
--   c) Not translate functions with constructor returns (leave them abstract)
--
-- This test documents the bug. Once fixed, it should return UNSAT because:
--   - X.value must be in {1, 2} (req value > 0 && value < 3)
--   - sum requires x1.value != x2.value, so {x1.value, x2.value} = {1, 2}
--   - The returned X has value = 1 + 2 = 3, which violates value < 3
--
-- @expected UNKNOWN

class X {
  value : Int
  req value > 0 && value < 3
}

fun sum(x1 : X, x2: X) : X {
  req x1.value != x2.value
  return X(value::x1.value+x2.value)
}
