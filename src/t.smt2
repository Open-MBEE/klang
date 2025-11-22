(set-option :produce-unsat-cores true)
; ================
; === Options: ===
; ================

(set-option :smt.macro-finder true)

; ===========================
; === Built-in datatypes: ===
; ===========================

(define-sort Ref () Int)

(declare-datatypes (T1 T2) ((Tuple2 (mk-Tuple2 (_1 T1)(_2 T2)))))
(declare-datatypes (T1 T2 T3) ((Tuple3 (mk-Tuple3 (_1 T1)(_2 T2)(_3 T3)))))

(define-sort Set (T) (Array T Bool))

; ===============================
; === User-defined datatypes: ===
; ===============================

(declare-sort TopLevelDeclarations) (declare-const mk-TopLevelDeclarations TopLevelDeclarations)
(declare-sort MathUtil_Spec) (declare-const mk-MathUtil_Spec MathUtil_Spec)
(declare-sort MathUtil) (declare-const mk-MathUtil MathUtil)

; =============
; === Heap: ===
; =============

(declare-datatypes () ((Any
  (lift-TopLevelDeclarations (sel-TopLevelDeclarations TopLevelDeclarations))
  (lift-MathUtil_Spec (sel-MathUtil_Spec MathUtil_Spec))
  (lift-MathUtil (sel-MathUtil MathUtil))
  null))
)

(declare-const heap (Array Ref Any))

(define-fun deref ((ref Ref)) Any
  (select heap ref)
)

; ==========================================
; === Class specific is/deref-functions: ===
; ==========================================

(define-fun deref-is-TopLevelDeclarations ((this Ref)) Bool
  (is-lift-TopLevelDeclarations (deref this))
)

(define-fun deref-TopLevelDeclarations ((this Ref)) TopLevelDeclarations
  (sel-TopLevelDeclarations (deref this))
)

(define-fun deref-is-MathUtil_Spec ((this Ref)) Bool
  (is-lift-MathUtil_Spec (deref this))
)

(define-fun deref-MathUtil_Spec ((this Ref)) MathUtil_Spec
  (sel-MathUtil_Spec (deref this))
)

(define-fun deref-is-MathUtil ((this Ref)) Bool
  (is-lift-MathUtil (deref this))
)

(define-fun deref-MathUtil ((this Ref)) MathUtil
  (sel-MathUtil (deref this))
)

; ======================
; === Isa-functions: ===
; ======================

(define-fun deref-isa-TopLevelDeclarations ((this Ref)) Bool
  (deref-is-TopLevelDeclarations this)
)

(define-fun deref-isa-MathUtil_Spec ((this Ref)) Bool
  (or
    (deref-is-MathUtil_Spec this)
    (deref-is-MathUtil this)
  )
)

(define-fun deref-isa-MathUtil ((this Ref)) Bool
  (deref-is-MathUtil this)
)

; ================
; === Getters: ===
; ================

; ================
; === Methods: ===
; ================

; ------------------------------------
;   Methods for class MathUtil_Spec:
; ------------------------------------

(declare-fun MathUtil_Spec.min (Ref Int Int) Int)

(define-fun MathUtil_Spec!min ((this Ref)(x Int)(y Int)) Int
  (MathUtil_Spec.min this x y)
)

(declare-fun MathUtil_Spec.abs (Ref Int) Int)

(define-fun MathUtil_Spec!abs ((this Ref)(x Int)) Int
  (MathUtil_Spec.abs this x)
)

; -------------------------------
;   Methods for class MathUtil:
; -------------------------------

(define-fun MathUtil.min ((this Ref)(x Int)(y Int)) Int
  (ite (> x y) x y)
)

(define-fun MathUtil!min ((this Ref)(x Int)(y Int)) Int
  (ite (> x y) x y)
)

(assert (!(forall ((this Ref)(x Int)(y Int))
  (=>
    (and
      (deref-is-MathUtil this)
    )
    (let (($result (MathUtil!min this x y)))
      (and
        (and (or (= $result x) (= $result y)) (and (<= $result x) (<= $result y)))
      )
    )
  )
) :named _xkassert0))

(define-fun MathUtil.abs ((this Ref)(x Int)) Int
  (ite (< x 0) (- x) x)
)

(define-fun MathUtil!abs ((this Ref)(x Int)) Int
  (ite (< x 0) (- x) x)
)

(assert (!(forall ((this Ref)(x Int))
  (=>
    (and
      (deref-is-MathUtil this)
    )
    (let (($result (MathUtil!abs this x)))
      (and
        (and (>= $result 0) (or (= $result x) (= $result (- x))))
      )
    )
  )
) :named _xkassert1))

; ===================
; === Invariants: ===
; ===================

; ---------------------------------------------
;   Invariant for class TopLevelDeclarations:
; ---------------------------------------------

; --- Constants: ---

(declare-const const-0-TopLevelDeclarations TopLevelDeclarations)



; --------------------------------------
;   Invariant for class MathUtil_Spec:
; --------------------------------------

; --- Constants: ---

(declare-const const-1-MathUtil_Spec MathUtil_Spec)
(declare-const const-2-MathUtil_Spec MathUtil_Spec)
(declare-const const-3-MathUtil_Spec MathUtil_Spec)
(declare-const const-4-MathUtil_Spec MathUtil_Spec)
(declare-const const-5-MathUtil_Spec MathUtil_Spec)



; ---------------------------------
;   Invariant for class MathUtil:
; ---------------------------------

; --- Constants: ---

(declare-const const-6-MathUtil MathUtil)
(declare-const const-7-MathUtil MathUtil)
(declare-const const-8-MathUtil MathUtil)
(declare-const const-9-MathUtil MathUtil)
(declare-const const-10-MathUtil MathUtil)




; ======================
; === Generate heap: ===
; ======================

(assert
  (=
    heap
    (store(store(store(store(store(store(store(store(store(store(store
      ((as const (Array Ref Any)) null)
        0 (lift-TopLevelDeclarations const-0-TopLevelDeclarations))
        1 (lift-MathUtil_Spec const-1-MathUtil_Spec))
        2 (lift-MathUtil_Spec const-2-MathUtil_Spec))
        3 (lift-MathUtil_Spec const-3-MathUtil_Spec))
        4 (lift-MathUtil_Spec const-4-MathUtil_Spec))
        5 (lift-MathUtil_Spec const-5-MathUtil_Spec))
        6 (lift-MathUtil const-6-MathUtil))
        7 (lift-MathUtil const-7-MathUtil))
        8 (lift-MathUtil const-8-MathUtil))
        9 (lift-MathUtil const-9-MathUtil))
        10 (lift-MathUtil const-10-MathUtil))
  )
)

(check-sat) (get-unsat-core) (exit)