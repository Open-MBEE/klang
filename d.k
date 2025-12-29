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

(declare-datatypes () ((TopLevelDeclarations (mk-TopLevelDeclarations (p Bool)(q Bool)(r Bool)(s Bool)))))

; =============
; === Heap: ===
; =============

(declare-datatypes () ((Any
  (lift-TopLevelDeclarations (sel-TopLevelDeclarations TopLevelDeclarations))
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

; ======================
; === Isa-functions: ===
; ======================

(define-fun deref-isa-TopLevelDeclarations ((this Ref)) Bool
  (deref-is-TopLevelDeclarations this)
)

; ================
; === Getters: ===
; ================

; -------------------------------------------
;   Getters for class TopLevelDeclarations:
; -------------------------------------------

; --- getters for TopLevelDeclarations.p: ---

(define-fun TopLevelDeclarations!p ((this Ref)) Bool
  (p (deref-TopLevelDeclarations this))
)

; --- getters for TopLevelDeclarations.q: ---

(define-fun TopLevelDeclarations!q ((this Ref)) Bool
  (q (deref-TopLevelDeclarations this))
)

; --- getters for TopLevelDeclarations.r: ---

(define-fun TopLevelDeclarations!r ((this Ref)) Bool
  (r (deref-TopLevelDeclarations this))
)

; --- getters for TopLevelDeclarations.s: ---

(define-fun TopLevelDeclarations!s ((this Ref)) Bool
  (s (deref-TopLevelDeclarations this))
)

; ================
; === Methods: ===
; ================

; ===================
; === Invariants: ===
; ===================

; ---------------------------------------------
;   Invariant for class TopLevelDeclarations:
; ---------------------------------------------

; --- Constants: ---

(declare-const const-0-TopLevelDeclarations TopLevelDeclarations)

; --- Invariant 1: ---

(define-fun TopLevelDeclarations.inv1 ((this Ref)) Bool
  (or (TopLevelDeclarations!p this) (TopLevelDeclarations!q this))
)

(assert (! (TopLevelDeclarations.inv1 0) :named _xkassert584))

; --- Invariant 2: ---

(define-fun TopLevelDeclarations.inv2 ((this Ref)) Bool
  (=> (TopLevelDeclarations!r this) (TopLevelDeclarations!s this))
)

(assert (! (TopLevelDeclarations.inv2 0) :named _xkassert585))

; --- Invariant 3: ---

(define-fun TopLevelDeclarations.inv3 ((this Ref)) Bool
  (=> (TopLevelDeclarations!s this) (= (TopLevelDeclarations!q this) (TopLevelDeclarations!r this)))
)

(assert (! (TopLevelDeclarations.inv3 0) :named _xkassert586))

; --- Invariant 4: ---

(define-fun TopLevelDeclarations.inv4 ((this Ref)) Bool
  (or (TopLevelDeclarations!p this) (TopLevelDeclarations!r this))
)

(assert (! (TopLevelDeclarations.inv4 0) :named _xkassert587))

; --- Invariant 5: ---

(define-fun TopLevelDeclarations.inv5 ((this Ref)) Bool
  (or (TopLevelDeclarations!r this) (TopLevelDeclarations!s this))
)

(assert (! (TopLevelDeclarations.inv5 0) :named _xkassert588))

; --- Invariant 6: ---

(define-fun TopLevelDeclarations.inv6 ((this Ref)) Bool
  (not (and (TopLevelDeclarations!r this) (TopLevelDeclarations!q this)))
)

(assert (! (TopLevelDeclarations.inv6 0) :named _xkassert589))

; --- Invariant 7: ---

(define-fun TopLevelDeclarations.inv7 ((this Ref)) Bool
  (not (and (TopLevelDeclarations!s this) (TopLevelDeclarations!p this)))
)

(assert (! (TopLevelDeclarations.inv7 0) :named _xkassert590))




; ======================
; === Generate heap: ===
; ======================

(assert
  (=
    heap
    (store
      ((as const (Array Ref Any)) null)
        0 (lift-TopLevelDeclarations const-0-TopLevelDeclarations))
  )
)


