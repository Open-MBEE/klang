# Timelines in K: representation, lowering, scaling findings and ideas

Handoff written 2026-11-06 (branch `feature/timelines`). Self-contained: no other repository is needed to read or reproduce the
klang parts. Background: the use case is a Europa Clipper pointing/attitude scheduler re-expressed in K. Slew durations and geometry
come from an external simulator as piecewise-linear (time, value) data, and K must look them up at times the solver chooses
(and must reproduce a Java reference scheduler to the millisecond).

## 1. What exists (implemented on this branch)

`src/k/frontend/TimelineSupport.scala`, with hooks in `TypeChecker.scala` (FunApplExp) and `CallApplExp.toSMT` in `AbstractSyntax.scala`.
No grammar change: these are intrinsics recognised by name.

| intrinsic | meaning |
|---|---|
| `timelineValue("f.csv", t, "ms"\|"us") : Duration` | CSV value column is seconds; `t` is a `Time`; result is rounded like Java `Math.round(s*1000)`, i.e. `floor(x + 1/2)` |
| `eventTime("f.csv", "name") : Time` | CSV columns `event,time` |
| `constDuration("f.csv", "NAME") : Duration` | CSV columns `name,seconds` |

Time and Duration lower to microseconds. Files resolve relative to the working directory, then `$KDATA_DIR`. The `k` runner changes
directory, so use absolute `.k` paths and/or `KDATA_DIR`.

Timeline CSV: header line, then `time,value[,segment]` (ISO-8601 UTC instants, seconds). The optional third column names a segment;
between the last row of one segment and the first row of the next the value is HELD constant (so one file can cover many disjoint
opportunities without interpolating across gaps).

### Lowering of `timelineValue(file, t, unit)`
1. Read rows `(T_k, V_k)` at model-processing time (times in microseconds since 1970, values in microseconds, exact BigDecimal).
2. Emit `(let ((tl_tN <t expr>)) (* unit (to_int (+ (/ TREE unit) 1/2))))`.
3. TREE is a balanced `ite` tree on `tl_tN < T_mid` over ALL segments of the file. Leaf k is
   `V_k + (dV/dT) * (tl_tN - T_k)` with exact rational constants, so it is linear in `t`: quantifier-free LIA/LRA, which keeps Z3
   Optimize (`minimize`/`maximize`/`soft req`) usable. Outside the sampled range the first/last segment is extrapolated (models must bound `t`).
4. Each call site gets its own copy of the tree.
5. `UnifiedSolver` skips its Optimize "verification" pass when a timeline was used (it was very slow on these models, and the plain Solver's model is authoritative).

## 2. Why it does not scale to a whole tour as is

- A per-opportunity timeline has 80 to ~900 rows (about 100-300 bytes of SMT per row): fine. A tour-wide file has up to ~34,000 rows,
  so every lookup is a 34,000-leaf tree (several MB of SMT).
- One opportunity needs 30-100 lookups once all activity tiers are modelled; there are ~69 opportunities. That is thousands of lookups,
  each carrying the whole tour: GBs of SMT. An "active" flag per object does not help (inactive objects still carry their trees).
- Nothing in the K text says that `t` lies in one opportunity's ~4 h window (about 1% of the rows).
- Z3 is fine when `t` folds to a constant (the tests collapse, one leaf survives) but must still parse/simplify the whole tree; when
  `t` is a genuine decision it must search the leaves.

## 3. Heap scalarization (implemented, default on; `-no-scalarize` disables)

Observation: chains of dependent lookups were slow as FIELDS of class instances but fast as top-level definitions, with the same SMT size
(about 425 KB for a 6-step chain over ~245-880-row timelines): class fields 25-42 s, top-level 0.4-3.3 s.

Cause: klang's heap encoding, not Z3 itself:
- Fields are accessors on datatype values living in `heap` (an `Array Ref Any` equated to a store chain), and `req x = ...` becomes an
  invariant over `(this Ref)`.
- A constructor-initialized reference property such as `ch : Chain = Chain()` was only constrained by
  `(= (deref (T!ch this)) (lift-Chain (mk-Chain const__1 ...)))`. With `r = (T!ch this)` unknown, `heap[r]` forces case splits.
  After `(apply (then simplify solve-eqs propagate-values simplify))` the top-level version collapsed to 729 bytes while the class version
  stayed at 1.3 MB.

Implementation (`AbstractSyntax.scala`, `ASTOptions.scalarizeHeap`):
- For each fixed-heap object, one `declare-const` per field and `(define-fun const-k-Class () Class (mk-Class f1 ... fn))`
  instead of `(declare-const const-k-Class Class)`.
- For a property initialized by a constructor call of a class with exactly ONE heap slot, additionally assert `(= (getter this) slot)`.

Results: same 6-step chain as class fields 42 s -> 1.9 s. Scalarization WITHOUT the pinned reference made it slower (87 s), so the
pinning is the important part. Self-contained demo with a small synthetic timeline: `src/test/TimelineChain.k` (361-row CSV in
`src/test/data/timeline_demo.csv`): 1.0 s with scalarization vs 3.6 s with `-no-scalarize`. The gap is much smaller than on real data
because the trees are small; on 245-880-row timelines and 6 chained steps it was 1.9 s vs 42 s.

Test suite (`./run-tests.sh -all -j 6`, 307 files): 250 pass / 57 unknown before and after, per-test status identical for the 208 result
lines the log parser could extract, total time 147.5 s vs 143.7 s (the 57 "unknown" are pre-existing). Limits: only helps classes with one
instance (which-instance reference choice remains a solver decision), nothing for heap-growth CEGAR, off for CVC5-compatible output, and
there is no test that specifically targets multi-instance/heap-growth cases. Z3 knobs (`smt.macro_finder`, `smt.arith.solver`,
`smt.relevancy`, tactic pipelines) did not help before the pinning change.

## 4. Idea: a K `Timeline` class (map from Time to value)

Desired surface (not implemented):
```
Timeline("file.csv", interpolation = linear | constant)
tl.value(t : Time)      // value at key t, else the interpolation between the two closest keys
```
Taken literally (arrays of keys + "the two closest points" as an existential index `k` with `T[k] <= t < T[k+1]`):
`v = V[k] + slope[k] * (t - T[k])` multiplies two unknowns (nonlinear), and expressing neighbours with `forall`/`exists` makes the problem
quantified. Z3 Optimize does not handle quantifiers/arrays well, so optimization would be lost. Hence keep the class as the readable
surface and choose the lowering underneath:

1. **`ite` tree over segments (current)**: quantifier-free, optimizable; needs pruning to the segments that can overlap `t`
   (bounds visible in the model, e.g. `t >= windowStart && t <= windowEnd`, or a segment key held by the object).
2. **Partial evaluation / constant folding (recommended first)**: most lookups in a Java-style dataflow have a ground argument once the
   event times are known. klang evaluates them in Scala and emits one number: no tree, no solver work. Trees only for lookups whose
   argument is a real decision (e.g. a placement variable). Needs a small evaluator for the K subset used: Time/Duration arithmetic,
   lookups, if-then-else, comparisons, and propagation through `req x = expr` definitions and (scalarized) fields.
3. **Pre-sampled grid** `A[t div D]` (no interpolation, quantifier-free since division by a constant is linear). Error up to slope*D
   (about 13 microseconds at D = 1 s for slews), so ms flips near rounding boundaries are possible; per-window only (4 years at 1 s is
   126M entries); a symbolic `select` over a big store chain is still a long `ite` chain in Z3.
4. **Different engine for lookup** (MiniZinc `element`, OR-tools CP-SAT `AddElement`; klang has `K2MiniZinc`): finite-domain index is native
   there, but the interpolation product, unit mixing and losing Z3 Optimize make it a poor fit.
5. **A library (Java/Python)**: klang already evaluates external calls concretely when all arguments are literal
   (`ExternalFunctions.tryEvaluate`); with symbolic arguments it falls back to CEGAR (slow). A library only helps if the frontend
   constant-folds first (= option 2 with the library as the evaluator) and cannot be used symbolically for real decisions.

Recommendation: `Timeline` class surface, backed by constant folding for ground arguments plus segment-pruned `ite` trees for decision
variables.

## 5. Millisecond-exactness facts about the reference that constrain any representation

(Reference = a Java scheduler that must be reproduced.)
- The Java rounds each slew component to a millisecond and then adds: `round(match) + round(slew)`, not `round(match + slew)`. A single
  pre-summed timeline is therefore not ms-exact (estimated 1 ms off in about a quarter of cases; not measured). Keep components separate
  where ms-exactness matters.
- The Java iterates (slew-from-end, scan-center searches) to a 1 s tolerance and uses the data of the LAST evaluation, not the exact fixed
  point. One scan duration changes ~0.1 s per second of centre time, so an exact fixed point was 78 ms off. Another search uses a 1 MINUTE
  tolerance, i.e. exactly two evaluations. The K model unrolls these iterations as chains of lookups.
- With approximation error bound `e`, a lookup is ms-exact unless the true value is within `e` of a rounding boundary (about 2e/1 ms).
  Some functions need a knot per second even at a 10 microsecond bound (about 1700 knots/hour), so a 100 microsecond bound was used and
  validated against the Java.
- One duration has a +-0.2 ms staircase caused by a ms-rounded intermediate inside a slew-angle formula; a piecewise-linear timeline
  cannot carry it (those durations match within 2 ms, starts exact).

## 6. Tour-wide encounter table (open design question: how K turns rows into objects)

A table with one row per scheduled encounter, so the K problem (declaring `planStart`/`planEnd`) can create one object per encounter whose
window intersects the period:
```
flyby,target,ca,ca_alt_km,approach_in,nadir_in,nadir_out,approach_out
G01,GANYMEDE,2030-10-30T14:08:22.996Z,250.003,2030-10-30T08:39:04.974Z,2030-10-30T12:42:12.663Z,2030-10-30T15:34:34.285Z,2030-10-30T19:38:07.517Z
```
`flyby` is also the `segment` value in the tour-wide timeline files, so a lookup can be restricted to that encounter's rows. Per-encounter
constants would be extra columns or a second file keyed by `flyby`. Undecided: a new intrinsic that instantiates objects from rows, a
fixed number N of objects with an indexed lookup, or something else. The user did not like the simple variants (reasons not stated);
ask before choosing.

## 7. How to reproduce

```sh
cd <klang worktree>; ./compile.sh
./export/k src/test/TimelineChain.k            # scalarization on (default)
./export/k src/test/TimelineChain.k -no-scalarize
./run-tests.sh -all -j 6                        # full suite (about 5 min)
```
The generated SMT of the last run is in `.tmp/k_unified.smt2`; append `(check-sat)` to run it through the `z3` CLI, or use
`(apply (then simplify solve-eqs propagate-values simplify))` to see what folds.
