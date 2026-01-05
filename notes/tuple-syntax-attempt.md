# Tuple Syntax Without `Tuple` Keyword - Attempt Notes

**Date:** 2026-01-04
**Goal:** Support `(1, true)` syntax instead of requiring `Tuple(1, true)`

## What We Tried

### 1. Added TupleExp Grammar Rule
Added to `expression` in Model.g4:
```antlr
| '(' expression (',' expression)+ ')' #TupleExp
```

### 2. Added `isLambda()` Predicate
The issue is that `(1, true)` conflicts with `pattern '->' expression` (LambdaExp) because `(1, true)` can be parsed as a CartesianPattern.

Added a semantic predicate to distinguish:
```antlr
@parser::members {
    private boolean isLambda() {
        // Look ahead for '->' after balanced parens or identifier
        // Returns true if this looks like a lambda, false otherwise
    }
}

expression:
    { isLambda() }? pattern '->' expression #LambdaExp
  | '(' expression (',' expression)+ ')' #TupleExp
  | '(' expression ')' #ParenExp
  ...
```

### 3. Discovered Parser File Location Issue
The parser files in `src/k/frontend/` were not being updated. ANTLR generates to `src/grammar/` but compilation uses `src/k/frontend/`. Required manual copy with package declaration:
```bash
for f in ModelParser.java ModelLexer.java ModelVisitor.java ModelBaseVisitor.java; do
  echo "// Generated from Model.g4 by ANTLR 4.7" > "src/k/frontend/$f"
  echo "package k.frontend;" >> "src/k/frontend/$f"
  tail -n +2 "src/grammar/$f" >> "src/k/frontend/$f"
done
```

## What Happened

### Success: Tuple Parsing Worked
After fixing the file copy issue, `(1, true)` parsed correctly as TupleExp.

### Failure: Test Regressions
8 tests failed (testsmt1, testsmt8, testsmt10-15, inheritance3) with type check errors.

### Root Cause: ATN Prediction Differences
The original parser (generated from klang2, same repo different clone) produces different ATN prediction behavior than regenerated parser:

1. **Block expressions in parens** like `({ k1: Int = 1; k1 } + { ... })` failed
2. **Bare identifiers at block end** like `k1` were parsed as `propertyDeclaration` instead of `expression`

Even regenerating the parser **without** the TupleExp change caused the same 8 test failures. The ATN structure differs between original and regenerated parsers.

### Attempted Fixes
1. **Reordered memberDeclaration** (expression before propertyDeclaration) - Fixed block expressions but broke `a = 42` property declarations
2. **Various predicate combinations** - None preserved original behavior

## Conclusion

The original parser files have ATN structures that work correctly with edge cases. Regenerating with ANTLR 4.7 (same version noted in original) produces subtly different prediction behavior.

**Options for future attempts:**
1. Find what makes the original generation different (ANTLR flags? grammar preprocessing?)
2. Modify KScalaVisitor to handle the different parse trees
3. Use a completely different approach (e.g., parse `(expr, expr)` as something else and convert in visitor)

## Current State
- Grammar file (Model.g4) has TupleExp rule added (can be reverted or kept for reference)
- Parser files restored to original from git
- All 169 tests pass
- Tuple syntax requires `Tuple(1, true)`
