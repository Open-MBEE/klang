# Examples Review - Issues Found in Copied Tests

This document tracks issues found when reviewing the 15 examples copied from `src/examples/` to `src/tests/` (as `ex_*.k` files).

## Summary

During the review, several examples were found to have issues that prevent them from being valid regression tests. This document tracks those issues and their resolution.

---

## Bank.k - CRITICAL BUG

**Status**: ❌ Remove from tests  
**Issue**: Set `isEmpty()` constraints are silently ignored

### Details
The test has constraints requiring non-empty sets:
```k
req HasAccounts: !(accounts.isEmpty())
req HasCustomers: !(customers.isEmpty())
```

But the solver returns SAT with empty sets, violating the constraints. See [notes/examples/Bank.txt](../notes/examples/Bank.txt) for full analysis.

### Actions
- [ ] Remove `ex_Bank.k` from `src/tests/`
- [ ] Add bogus baseline to ensure test fails if accidentally re-added
- [ ] Fix the `isEmpty()` translation to SMT (CRITICAL BUG)

---

## b.k - Diamond Inheritance Policy ✅ FIXED

**Status**: ✅ Working correctly  
**Fixed**: 2026-01-19

### Current Policy (per TypeChecker.scala)
```scala
// Auto-share all diamond ancestors by default (unless explicitly renamed)
// This makes multiple inheritance "just work" in the common case
val shareTypes = expandedShareTypes ++ diamondAncestors

// Note: With auto-sharing of diamond ancestors, we no longer error on unshared diamonds.
// All diamond inheritance is automatically resolved by sharing.
```

### The Bug (FIXED)
The `findDiamondAncestors` function was using intersection which only finds
ancestors appearing in ALL paths. Changed to count occurrences and return
any ancestor appearing in 2+ paths.

### Actions (COMPLETED)
- [x] Document current policy in notes/examples/b.k.txt
- [x] Fix the typecheck bug that prevents auto-sharing from working
- [x] Update `b.k` to have `@expected SAT` annotation
- [x] ex_b.k was already removed

---

## b2.k - Multiple Share Declarations

**Status**: ⚠️ Needs modification  
**Issue**: Uses multiple `share` declarations when one should suffice

### Current Code
```k
class BB extends A, C, E {
  share F
  share E
  share D
  y : Int
}
```

### Expected Behavior
With the nearest-parent sharing fix, only `share F` (the nearest parent creating the diamond) should be needed.

### Actions
- [ ] Modify b2.k to use single share for nearest parent
- [ ] If still not working, investigate and fix
- [ ] Consider removing ex_b2.k from tests if fundamental issues remain

---

## c.k - Excessive Instances in Model

**Status**: ⚠️ Low Priority  
**Issue**: Solution includes instances for classes in imported packages

### Current Behavior
When solving `c.k`, the model includes instances for every class in the import chain (D, E, F, etc.), not just the instance of C that we care about.

### Desired Behavior
Minimal model with just the C instance. The purpose of creating instances is to verify the class is not inconsistent, but verifying all imported classes is wasteful.

### Actions
- [ ] Investigate how to limit instance creation to relevant classes only
- [ ] LOW PRIORITY - May require significant solver changes

---

## conservative-extension.k - Unverified Behavior

**Status**: ❓ Needs verification  
**Issue**: Comments claim wrong answers based on constrained values of B

### Concerning Comments
```k
-- p & q => p:
-- incorrectly succeeds when 12
-- correctly   succeeds when 40

-- p & q = p:
-- correctly   fails when 12
-- incorrectly fails when 40
```

### Actions
- [ ] Test with B=12 and B=40 to verify behavior
- [ ] Document findings in notes/examples/conservative-extension.txt
- [ ] Remove ex_conservative-extension.k from tests until verified

---

## Inheritance-Related Cases

**Status**: ⚠️ Needs comprehensive review  
**Issue**: Various inheritance tests make conflicting assumptions

### Files to Review

**In src/tests/:**
- `inheritance1.k` through `inheritance12.k`
- `diamond_autoshare.k`
- `diamond_deep.k`
- `diamond_mixed_share_rename.k`
- `diamond_rename.k`
- `diamond_share.k`
- `diamond_share_multiple.k`
- `rename_*.k`
- `shadow_field.k`

**In src/examples/:**
- `b.k`, `b2.k`
- `Shapes.k` (the `Wrong` line issue)

### Actions
- [ ] Review all inheritance tests for consistency with current policy
- [ ] Update @expected annotations where needed
- [ ] Document canonical inheritance/diamond behavior

---

## DSN_Pass-diagnostic.k - Timeout

**Status**: ✅ Not in tests (good)  
**Issue**: Times out during solving

### Details
This is a diagnostic file that times out. It's not in `tests/`, which is correct.

### Actions
- [ ] Add notes/examples/DSN_Pass-diagnostic.txt documenting timeout behavior

---

## DSN_Pass.k - Unclear Result

**Status**: ❓ Needs verification  
**Issue**: Unclear if result is SAT or TIMEOUT (partial model)

### Details
The solver output for DSN_Pass.k doesn't clearly indicate whether the result is:
- **SAT**: A complete satisfying model
- **TIMEOUT**: Only a partial model was found

### Actions
- [ ] Scrutinize output to determine actual result
- [ ] If result is valid SAT, update baseline and add to tests
- [ ] Improve solver output clarity to distinguish SAT from partial/timeout results

---

## fibonacci.k - Recursive Function Limitation

**Status**: ❌ Remove from tests  
**Issue**: Half the file is unnecessary; `fib()` is commented out

### Details
The fibonacci implementation is commented out because recursive functions like `fib(x)` don't work.
The file in its current state is not a meaningful test.

### Root Cause
**Recursive functions are not supported** - this is a fundamental limitation.

### Actions
- [ ] Remove `ex_fibonacci.k` from `src/tests/`
- [ ] Uncomment `fib()` lines in `src/examples/fibonacci.k` to document the limitation
- [ ] Update `@expected` to ERROR (typecheck failure)
- [ ] **ROADMAP HIGH PRIORITY**: Support recursive functions (may be challenging)

---

## Fruits.k - Looks Good

**Status**: ✅ Keep in tests  
**Issue**: None - baseline appears correct

### Details
The new baseline for ex_Fruits.k looks good. Output has improved compared to earlier runs.

### Actions
- [ ] Verify baseline is correct (done - looks good)

---

## General: Example Baselines

**Status**: ⚠️ Needs attention  
**Issue**: Not all examples have baselines; some may have been blindly updated

### Concerns
- Need to ensure all examples have baselines
- Must verify expected output is correct before creating baselines
- Baselines should fail when there are undesirable results

### Actions
- [ ] Audit which examples have baselines
- [ ] Create baselines for examples that lack them
- [ ] Verify each baseline represents correct expected behavior
- [ ] Don't blindly update baselines - understand what changed

---

## function_with_class_params*.k - Not in Tests

**Status**: ❓ Needs investigation  
**Issue**: Appear to pass but weren't copied to tests

### Questions
1. Why weren't these copied to tests? Are they redundant with existing tests?
2. Did we back out the capability of creating extra objects to verify uncalled functions?
3. Should there be function synthesis based on whether a function has constraints?

### Related Files
- `function_with_class_params.k`
- `function_with_class_params2.k`
- `function_with_class_params3.k`
- `mathutil.k` (same question about extra objects)

### Actions
- [ ] Check if these tests are redundant with others in `tests/`
- [ ] Investigate extra object creation for uncalled function verification
- [ ] Clarify function synthesis policy and document decision

---

## GravityScience.k - Needs Scrutiny

**Status**: ❓ Needs verification  
**Issue**: May just need baseline update

### Actions
- [ ] Run and scrutinize output for problems
- [ ] If correct, update baseline and add to tests

---

## library.k - Notes Look Accurate

**Status**: ✅ Not in tests (correctly)  
**Issue**: Features not implemented (per notes)

### Actions
- [ ] Verify features are captured in roadmap .md file

---

## lightswitch.k - Confusing Output

**Status**: ❓ Needs investigation  
**Issue**: Synthesized function returns 1/2 instead of expected 0/1?

### Details
The function synthesis output is confusing. Should the synthesized function be returning 0 or 1 instead of 1 or 2?

### Actions
- [ ] Investigate expected vs actual output
- [ ] Determine if this is a bug or expected behavior

---

## lisp.k - Looks Good

**Status**: ✅ Keep in tests (as ex_lisp.k)  
**Issue**: None - baseline appears correct

### Actions
- [ ] Update baseline in `examples/` for lisp.k
- [ ] Update notes in `examples/` for lisp.k

---

## Tracking

| File | Issue | Priority | Removed from tests? | Fixed? |
|------|-------|----------|---------------------|--------|
| Bank.k | isEmpty() ignored | CRITICAL | ❌ | ❌ |
| b.k | Diamond auto-sharing | HIGH | N/A | ✅ FIXED |
| b2.k | Multiple shares | MEDIUM | ❌ | ❌ |
| c.k | Excessive instances | LOW | N/A | ❌ |
| conservative-extension.k | Unverified behavior | MEDIUM | ❌ | ❌ |
| DSN_Pass-diagnostic.k | Timeout | LOW | N/A (not in tests) | N/A |
| DSN_Pass.k | Unclear result | MEDIUM | N/A | ❌ |
| fibonacci.k | Recursive functions | HIGH | ❌ | ❌ |
| Fruits.k | None | - | ✅ Keep | ✅ |
| function_with_class_params*.k | Not in tests | MEDIUM | N/A | ❓ |
| GravityScience.k | Needs scrutiny | MEDIUM | N/A | ❓ |
| library.k | Notes accurate | LOW | N/A (correct) | N/A |
| lightswitch.k | Confusing output | MEDIUM | N/A | ❓ |
| lisp.k | Looks good | - | ✅ Keep | ✅ |
| mathutil.k | Extra objects? | MEDIUM | ✅ Keep | ❓ |
| order.k | Should NOT pass | HIGH | N/A | ❌ |
| prepost.k | Uncalled functions | MEDIUM | ❓ | ❓ |
| seq_vs_array.k | Model extraction bug? | HIGH | ❌ | ❌ |
| Shapes.k | Ready for tests | LOW | N/A (add) | ✅ |
| sm.k | Ready for tests | LOW | N/A (add) | ✅ |
| sysml.k | Should FAIL | HIGH | N/A | ❌ |
| testsmt.k | Ready for tests | LOW | N/A (add) | ✅ |

---

## order.k - Should NOT Pass

**Status**: ❌ Needs fix  
**Issue**: Test passes but should fail

### Actions
- [ ] Investigate why order.k passes when it shouldn't
- [ ] Fix the test or the solver behavior

---

## prepost.k - Uncalled Functions

**Status**: ❓ Needs review  
**Issue**: May be affected by uncalled function verification policy

### Actions
- [ ] Review behavior based on decision about adding objects to verify uncalled functions

---

## seq_vs_array.k - Model Extraction Bug

**Status**: ❌ Remove from tests  
**Issue**: Output unclear, likely bug extracting solution from model

### Details
The output doesn't appear correct. This looks like a bug in extracting the solution from the Z3 model.

### Actions
- [ ] Pull ex_seq_vs_array.k from tests
- [ ] Have it fail in examples
- [ ] Fix the model extraction bug

---

## Shapes.k - Ready for Tests

**Status**: ✅ Add to tests  
**Issue**: None - just needs baseline update

### Actions
- [ ] Update Shapes.k baseline
- [ ] Copy to tests

---

## sm.k - Ready for Tests

**Status**: ✅ Add to tests  
**Issue**: None - just needs baseline update

### Actions
- [ ] Update sm.k baseline
- [ ] Copy to tests

---

## sysml.k - Should Fail

**Status**: ❌ Needs fix  
**Issue**: Test should fail but doesn't

### Actions
- [ ] Investigate why sysml.k doesn't fail when it should
- [ ] Fix the test or the solver behavior

---

## testsmt.k - Ready for Tests

**Status**: ✅ Add to tests  
**Issue**: None - just needs baseline update

### Actions
- [ ] Update testsmt.k baseline
- [ ] Copy to tests

---

*Last Updated: 2026-01-19*

