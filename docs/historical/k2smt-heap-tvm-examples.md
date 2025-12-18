# K2SMT Heap & TimeVaryingMap Examples

This document collects three concrete mini–backend examples for K → SMT,
each with:

- A small **K-style program snippet** (illustrative, not syntax-checked),
- The corresponding **SMT-LIB2 encoding** for:
  - Plain bounded heap,
  - Bounded heap with `assert-soft` minimization,
  - And a CEGAR-friendly `heap_full` flag (where relevant).

You can adapt these patterns directly in `K2SMT` and the unified solver.

---

## Example 1: Bounded Heap of Objects in a `Seq<C>`

### 1.1 K-style program

```k
package examples.heap.seq;

class C {
  Int id;
}

class Model {
  Seq<C> seq;

  // Optional constraint: all elements (if any) are non-null
  constraint NonNullElements {
    forall i in 0 .. seq.length - 1 {
      seq[i] != null;
    }
  }
}
```

Assumptions for the SMT encoding:

- One dynamic class `C`.
- A bounded pool of candidate objects `c0 .. c(N_C-1)`.
- The field `Model.seq` is represented as:
  - an `Array Int C` named `seq`,
  - with an integer length `len_seq`,
  - and a maximum length `MAX_SEQ`.

Below, we instantiate a small example with `N_C = 3` and `MAX_SEQ = 4`.

---

### 1.2 Shared SMT-LIB2 declarations

Used by all variants in this example:

```smt2
; ============================================================
; Example 1: shared declarations for C and seq : Seq[C]
; ============================================================

(set-logic ALL) ; or QF_UF + QF_LIA, depending on the rest of the model

; One uninterpreted sort for C objects
(declare-sort C 0)

; A distinguished null / "no object" value
(declare-const null_C C)

; Bounded pool of candidate objects (c0 .. c2)
(declare-const c0 C)
(declare-const c1 C)
(declare-const c2 C)

; Optional: enforce they are all distinct and non-null
(assert (distinct null_C c0 c1 c2))

; Sequence representation: seq : Seq[C] with max length MAX_SEQ = 4
(declare-const len_seq Int)
(declare-const seq (Array Int C))

; Bounds on length
(assert (>= len_seq 0))
(assert (<= len_seq 4))

; For i >= len_seq, seq[i] = null_C (unrolled for 0..3)

; i = 0
(assert (=> (not (< 0 len_seq)) (= (select seq 0) null_C)))
; i = 1
(assert (=> (not (< 1 len_seq)) (= (select seq 1) null_C)))
; i = 2
(assert (=> (not (< 2 len_seq)) (= (select seq 2) null_C)))
; i = 3
(assert (=> (not (< 3 len_seq)) (= (select seq 3) null_C)))
```

---

### 1.3 Variant A: Plain bounded heap (hard bound only)

Here, we enforce that **in-range** sequence elements come from the pool
`{null_C, c0, c1, c2}`. That alone bounds the number of distinct `C`
objects the solver can use.

```smt2
; ------------------------------------------------------------
; Variant A: Plain bounded heap for seq : Seq[C]
; ------------------------------------------------------------

; For all in-range i, seq[i] ∈ {null_C, c0, c1, c2}
; (unrolled for i = 0..3)

; Helper (informal):
;   (inPool x) := (or (= x null_C) (= x c0) (= x c1) (= x c2))

; i = 0
(assert (=> (< 0 len_seq)
            (or (= (select seq 0) null_C)
                (= (select seq 0) c0)
                (= (select seq 0) c1)
                (= (select seq 0) c2))))

; i = 1
(assert (=> (< 1 len_seq)
            (or (= (select seq 1) null_C)
                (= (select seq 1) c0)
                (= (select seq 1) c1)
                (= (select seq 1) c2))))

; i = 2
(assert (=> (< 2 len_seq)
            (or (= (select seq 2) null_C)
                (= (select seq 2) c0)
                (= (select seq 2) c1)
                (= (select seq 2) c2))))

; i = 3
(assert (=> (< 3 len_seq)
            (or (= (select seq 3) null_C)
                (= (select seq 3) c0)
                (= (select seq 3) c1)
                (= (select seq 3) c2))))
```

The solver is now restricted to this finite heap. There are no soft
constraints and no `heap_full` flag in this variant.

---

### 1.4 Variant B: Bounded heap + `assert-soft` minimization

Now we add an `Alive_C` predicate and soft constraints to **minimize**
the number of live objects:

```smt2
; ------------------------------------------------------------
; Variant B: Bounded heap with Alive_C and soft minimization
; ------------------------------------------------------------

; A predicate indicating whether a candidate object is actually "allocated"
(declare-fun Alive_C (C) Bool)

; Sequence elements that are non-null must be alive
; i = 0
(assert (=> (< 0 len_seq)
            (=> (not (= (select seq 0) null_C))
                (Alive_C (select seq 0)))))
; i = 1
(assert (=> (< 1 len_seq)
            (=> (not (= (select seq 1) null_C))
                (Alive_C (select seq 1)))))
; i = 2
(assert (=> (< 2 len_seq)
            (=> (not (= (select seq 2) null_C))
                (Alive_C (select seq 2)))))
; i = 3
(assert (=> (< 3 len_seq)
            (=> (not (= (select seq 3) null_C))
                (Alive_C (select seq 3)))))

; Soft constraints to prefer fewer alive objects:
; Encourage Alive_C(c0), Alive_C(c1), Alive_C(c2) to be false unless required.

(assert-soft (not (Alive_C c0)) :weight 1)
(assert-soft (not (Alive_C c1)) :weight 1)
(assert-soft (not (Alive_C c2)) :weight 1)
```

You would use Z3's `Optimize` interface to interpret these `assert-soft`
constraints and obtain a model that uses as few live `C` objects as
possible.

---

### 1.5 Variant C: Bounded + soft + CEGAR-friendly `k!heap_full!C`

Finally, we add a flag that signals when **all** candidate objects are
required by the current solution, which is useful for heap‑bound CEGAR:

```smt2
; ------------------------------------------------------------
; Variant C: heap_full flag for class C
; ------------------------------------------------------------

(declare-const k!heap_full!C Bool)

; k!heap_full!C is true iff all candidate objects are alive.
(assert (= k!heap_full!C
           (and (Alive_C c0)
                (Alive_C c1)
                (Alive_C c2))))
```

On the Scala side, the CEGAR loop can inspect `k!heap_full!C`:

- If `false`, the heap bound is probably sufficient.
- If `true`, the bound `N_C = 3` is “tight”; increasing it is a good
  refinement step.

---

## Example 2: Activities and TimeVaryingMap (TVM) with Capacity

### 2.1 K-style program

```k
package examples.planning.tvm;

class Activity {
  Int start;
  Int end;
}

class Plan {
  Seq<Activity> activities;
  TimeVaryingMap<Int> resourceUsage;

  constraint ResourceUsageDefinition {
    forall t in 0 .. 4 {
      resourceUsage[t] =
        count { a in activities |
          a.start <= t && t < a.end
        };
    }
  }

  constraint Capacity {
    forall t in 0 .. 4 {
      resourceUsage[t] <= 2;
    }
  }
}
```

Assumptions:

- One activity class `Activity` (SMT sort `A`).
- Bounded pool of candidate activities `a0 .. a2`.
- Timeline (horizon) is discrete: `t ∈ {0,1,2,3,4}`.
- Capacity = 2 units.

---

### 2.2 Bounded + `Alive_A` + TVM + soft + `k!heap_full!A`

This is the "rich" variant (bounded heap, soft constraints, heap_full):

```smt2
; ============================================================
; Example 2: Activity + TVM base encoding
; ============================================================

(set-logic QF_LIA)

; Activities
(declare-sort A 0)
(declare-const a0 A)
(declare-const a1 A)
(declare-const a2 A)

; Alive predicate
(declare-fun Alive_A (A) Bool)

; Start/end times, horizon [0..4]
(declare-fun start (A) Int)
(declare-fun end   (A) Int)

; Basic interval well-formedness within horizon
(assert (and (>= (start a0) 0) (< (start a0) (end a0)) (<= (end a0) 5)))
(assert (and (>= (start a1) 0) (< (start a1) (end a1)) (<= (end a1) 5)))
(assert (and (>= (start a2) 0) (< (start a2) (end a2)) (<= (end a2) 5)))

; If not alive, force degenerate interval (optional modeling choice)
(assert (=> (not (Alive_A a0)) (= (start a0) (end a0))))
(assert (=> (not (Alive_A a1)) (= (start a1) (end a1))))
(assert (=> (not (Alive_A a2)) (= (start a2) (end a2))))

; Resource usage TVM: usage[t], t ∈ [0..4]
(declare-const usage (Array Int Int))

; Capacity = 2
(declare-const capacity Int)
(assert (= capacity 2))

; Explicit usage variables
(declare-const u0 Int)
(declare-const u1 Int)
(declare-const u2 Int)
(declare-const u3 Int)
(declare-const u4 Int)

; u0 .. u4 count alive activities active at each time

(assert (= u0
  (+ (ite (and (Alive_A a0)
               (<= (start a0) 0) (< 0 (end a0))) 1 0)
     (ite (and (Alive_A a1)
               (<= (start a1) 0) (< 0 (end a1))) 1 0)
     (ite (and (Alive_A a2)
               (<= (start a2) 0) (< 0 (end a2))) 1 0)
  )))

(assert (= u1
  (+ (ite (and (Alive_A a0)
               (<= (start a0) 1) (< 1 (end a0))) 1 0)
     (ite (and (Alive_A a1)
               (<= (start a1) 1) (< 1 (end a1))) 1 0)
     (ite (and (Alive_A a2)
               (<= (start a2) 1) (< 1 (end a2))) 1 0)
  )))

(assert (= u2
  (+ (ite (and (Alive_A a0)
               (<= (start a0) 2) (< 2 (end a0))) 1 0)
     (ite (and (Alive_A a1)
               (<= (start a1) 2) (< 2 (end a1))) 1 0)
     (ite (and (Alive_A a2)
               (<= (start a2) 2) (< 2 (end a2))) 1 0)
  )))

(assert (= u3
  (+ (ite (and (Alive_A a0)
               (<= (start a0) 3) (< 3 (end a0))) 1 0)
     (ite (and (Alive_A a1)
               (<= (start a1) 3) (< 3 (end a1))) 1 0)
     (ite (and (Alive_A a2)
               (<= (start a2) 3) (< 3 (end a2))) 1 0)
  )))

(assert (= u4
  (+ (ite (and (Alive_A a0)
               (<= (start a0) 4) (< 4 (end a0))) 1 0)
     (ite (and (Alive_A a1)
               (<= (start a1) 4) (< 4 (end a1))) 1 0)
     (ite (and (Alive_A a2)
               (<= (start a2) 4) (< 4 (end a2))) 1 0)
  )))

; Tie usage[t] to u_t
(assert (= (select usage 0) u0))
(assert (= (select usage 1) u1))
(assert (= (select usage 2) u2))
(assert (= (select usage 3) u3))
(assert (= (select usage 4) u4))

; Capacity constraints
(assert (<= u0 capacity))
(assert (<= u1 capacity))
(assert (<= u2 capacity))
(assert (<= u3 capacity))
(assert (<= u4 capacity))

; Soft constraints: prefer activities to be NOT alive
(assert-soft (not (Alive_A a0)) :weight 1)
(assert-soft (not (Alive_A a1)) :weight 1)
(assert-soft (not (Alive_A a2)) :weight 1)

; CEGAR-friendly "heap full" flag for activity class A
(declare-const k!heap_full!A Bool)
(assert (= k!heap_full!A
           (and (Alive_A a0)
                (Alive_A a1)
                (Alive_A a2))))
```

This corresponds closely to the K-level idea of:

- A `Plan.activities` sequence,
- A `resourceUsage[t]` that counts active activities,
- A capacity constraint,
- And an implicit goal of minimizing the number of scheduled activities,
  encoded via `assert-soft`.

---

## Example 3: Activities + TVM + Hard Coverage Goals

Now we extend Example 2 with concrete **hard requirements**:

1. At least one activity is active at `t = 2`.
2. Full coverage over the horizon `[0..4]`.

### 3.1 K-style program with goals

```k
package examples.planning.tvm.goals;

class Activity {
  Int start;
  Int end;
}

class Plan {
  Seq<Activity> activities;
  TimeVaryingMap<Int> resourceUsage;

  constraint ResourceUsageDefinition {
    forall t in 0 .. 4 {
      resourceUsage[t] =
        count { a in activities |
          a.start <= t && t < a.end
        };
    }
  }

  constraint Capacity {
    forall t in 0 .. 4 {
      resourceUsage[t] <= 2;
    }
  }

  // Goal 1: at least one activity at t = 2
  constraint CoverTime2 {
    resourceUsage[2] >= 1;
  }

  // Goal 2: full coverage of the horizon
  constraint FullCoverage {
    forall t in 0 .. 4 {
      resourceUsage[t] >= 1;
    }
  }
}
```

### 3.2 SMT-LIB2: Add the coverage requirements

We start from the encoding in Example 2 (bounded + Alive_A + TVM + soft +
k!heap_full!A) and **add**:

```smt2
; ============================================================
; Example 3: add coverage goals on top of Example 2 encoding
; ============================================================

; Requirement 1: "at least one activity at t = 2"
(assert (>= u2 1))

; Requirement 2: "full coverage over [0..4]"
(assert (>= u0 1))
(assert (>= u1 1))
(assert (>= u2 1))
(assert (>= u3 1))
(assert (>= u4 1))
```

Combined with:

- The capacity constraints: `u_t <= 2` for each `t`,
- The soft constraints minimizing `Alive_A(ai)`,

this becomes a simple but nontrivial **resource‑constrained planning**
problem:

- Hard: cover all time points without exceeding capacity.
- Soft: use as few activities as possible.
- Heap: bounded pool of activities `a0..a2`, with
  `k!heap_full!A` indicating when the bound is “full”.

Your CEGAR loop can then:

- On `sat`: read `k!heap_full!A` to see if the bound is tight.
- On `unsat`: decide whether to increase the bound `N_A` and regenerate
  the SMT model.

---

You can use these three examples as:

- Templates in `K2SMT` for encoding:
  - Bounded heaps,
  - TimeVaryingMaps,
  - Soft constraints and heap-full flags.
- Regression examples to test:
  - Unbounded vs bounded vs CEGAR‑bounded heap strategies,
  - With and without Optimize/soft constraints.
