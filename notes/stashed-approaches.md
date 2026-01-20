# Stashed Approaches

This file tracks experimental approaches that were stashed for potential future use.

## polymorphic-heap-slots-approach (2026-01-18)

**Stash command**: `git stash push -m "polymorphic-heap-slots-approach"`

**To restore**: `git stash apply stash^{/polymorphic-heap-slots-approach}`

**Description**: 
An approach to subtype polymorphism in the heap where each heap SLOT could hold any type in its inheritance hierarchy. This involved:
- Adding `heapAlternativeConstants: Map[Int, List[(String, String)]]` to UtilSMT
- For each slot, declaring constants for all classes in the hierarchy
- Generating `(assert (or ...))` constraints so Z3 chooses which type to instantiate at each slot
- Modified heap initialization to handle polymorphic slots

**Why stashed**:
The approach was complex and still resulted in UNSAT for lisp.k. A simpler approach was proposed:
- Keep slots definitively typed
- Expand valid reference ranges (a `S_Exp` ref can point to Atom or ListExp slots)
- Add `used` flags to instances so invariants only apply when instance is used

**Key changes were in**:
- `src/k/frontend/AbstractSyntax.scala` - UtilSMT, EntityDecl.toSMTInvariant, Model.toSMT
