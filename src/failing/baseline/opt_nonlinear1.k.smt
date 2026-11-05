; ================
; === Options: ===
; ================

(set-option :smt.macro-finder true)

; ======================
; === String Theory: ===
; ======================

; Z3 4.13.0+ requires explicit logic or theory declaration for strings
(set-logic ALL)

; ===========================
; === Built-in datatypes: ===
; ===========================

(define-sort Ref () Int)

(declare-datatypes (T1 T2) ((Tuple2 (mk-Tuple2 (_1 T1)(_2 T2)))))
(declare-datatypes (T1 T2 T3) ((Tuple3 (mk-Tuple3 (_1 T1)(_2 T2)(_3 T3)))))


; ===============================
; === User-defined datatypes: ===
; ===============================

(declare-datatypes () ((TopLevelDeclarations (mk-TopLevelDeclarations (main Ref)))))
(declare-datatypes () ((OptNonlinear1 (mk-OptNonlinear1 (x Int)))))

; =============
; === Heap: ===
; =============

(declare-datatypes () ((Any
  (lift-TopLevelDeclarations (sel-TopLevelDeclarations TopLevelDeclarations))
  (lift-OptNonlinear1 (sel-OptNonlinear1 OptNonlinear1))
  null))
)

(declare-const heap (Array Ref Any))
(declare-const NULL$ String)
(declare-const NULL$Ref Ref)
(assert (= NULL$Ref (- 1)))

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

(define-fun deref-is-OptNonlinear1 ((this Ref)) Bool
  (is-lift-OptNonlinear1 (deref this))
)

(define-fun deref-OptNonlinear1 ((this Ref)) OptNonlinear1
  (sel-OptNonlinear1 (deref this))
)

; ======================
; === Isa-functions: ===
; ======================

(define-fun deref-isa-TopLevelDeclarations ((this Ref)) Bool
  (deref-is-TopLevelDeclarations this)
)

(define-fun deref-isa-OptNonlinear1 ((this Ref)) Bool
  (deref-is-OptNonlinear1 this)
)

; ================
; === Getters: ===
; ================

; -------------------------------------------
;   Getters for class TopLevelDeclarations:
; -------------------------------------------

; --- getters for TopLevelDeclarations.main: ---

(define-fun TopLevelDeclarations!main ((this Ref)) Ref
  (main (deref-TopLevelDeclarations this))
)

; ------------------------------------
;   Getters for class OptNonlinear1:
; ------------------------------------

; --- getters for OptNonlinear1.x: ---

(define-fun OptNonlinear1!x ((this Ref)) Int
  (x (deref-OptNonlinear1 this))
)

; ================
; === Methods: ===
; ================

; ------------------------------------
;   Methods for class OptNonlinear1:
; ------------------------------------

(define-fun OptNonlinear1.cost ((this Ref)(v Int)) Int
  (- 100 (* (- v 7) (- v 7)))
)

(define-fun OptNonlinear1!cost ((this Ref)(v Int)) Int
  (- 100 (* (- v 7) (- v 7)))
)

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
  (deref-isa-OptNonlinear1 (TopLevelDeclarations!main this))
)

(assert (! (TopLevelDeclarations.inv1 0) :named _xkassert0))



; --------------------------------------
;   Invariant for class OptNonlinear1:
; --------------------------------------

; --- Constants: ---

(declare-const const-1-OptNonlinear1 OptNonlinear1)

; --- Invariant 1: ---

(define-fun OptNonlinear1.inv1 ((this Ref)) Bool
  (>= (OptNonlinear1!x this) 0)
)

(assert (! (OptNonlinear1.inv1 1) :named _xkassert1))

; --- Invariant 2: ---

(define-fun OptNonlinear1.inv2 ((this Ref)) Bool
  (<= (OptNonlinear1!x this) 10)
)

(assert (! (OptNonlinear1.inv2 1) :named _xkassert2))




; ================================
; === Optimization Objectives: ===
; ================================

(maximize (let ((this 1)) (OptNonlinear1!cost this (OptNonlinear1!x this))))

; ======================
; === Generate heap: ===
; ======================

(assert
  (=
    heap
    (store(store
      ((as const (Array Ref Any)) null)
        0 (lift-TopLevelDeclarations const-0-TopLevelDeclarations))
        1 (lift-OptNonlinear1 const-1-OptNonlinear1))
  )
)

