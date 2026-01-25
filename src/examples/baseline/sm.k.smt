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

(declare-sort Event) (declare-const mk-Event Event)
(declare-sort State) (declare-const mk-State State)
(declare-datatypes () ((RocketMachine (mk-RocketMachine (off Ref)(ready Ref)(firing Ref)(turn_on Ref)(fire Ref)(turn_off Ref)))))

; =============
; === Heap: ===
; =============

(declare-datatypes () ((Any
  (lift-Event (sel-Event Event))
  (lift-State (sel-State State))
  (lift-RocketMachine (sel-RocketMachine RocketMachine))
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

(define-fun deref-is-Event ((this Ref)) Bool
  (is-lift-Event (deref this))
)

(define-fun deref-Event ((this Ref)) Event
  (sel-Event (deref this))
)

(define-fun deref-is-State ((this Ref)) Bool
  (is-lift-State (deref this))
)

(define-fun deref-State ((this Ref)) State
  (sel-State (deref this))
)

(define-fun deref-is-RocketMachine ((this Ref)) Bool
  (is-lift-RocketMachine (deref this))
)

(define-fun deref-RocketMachine ((this Ref)) RocketMachine
  (sel-RocketMachine (deref this))
)

; ======================
; === Isa-functions: ===
; ======================

(define-fun deref-isa-Event ((this Ref)) Bool
  (deref-is-Event this)
)

(define-fun deref-isa-State ((this Ref)) Bool
  (deref-is-State this)
)

(define-fun deref-isa-RocketMachine ((this Ref)) Bool
  (deref-is-RocketMachine this)
)

; ================
; === Getters: ===
; ================

; ------------------------------------
;   Getters for class RocketMachine:
; ------------------------------------

; --- getters for RocketMachine.off: ---

(define-fun RocketMachine!off ((this Ref)) Ref
  (off (deref-RocketMachine this))
)

; --- getters for RocketMachine.ready: ---

(define-fun RocketMachine!ready ((this Ref)) Ref
  (ready (deref-RocketMachine this))
)

; --- getters for RocketMachine.firing: ---

(define-fun RocketMachine!firing ((this Ref)) Ref
  (firing (deref-RocketMachine this))
)

; --- getters for RocketMachine.turn_on: ---

(define-fun RocketMachine!turn_on ((this Ref)) Ref
  (turn_on (deref-RocketMachine this))
)

; --- getters for RocketMachine.fire: ---

(define-fun RocketMachine!fire ((this Ref)) Ref
  (fire (deref-RocketMachine this))
)

; --- getters for RocketMachine.turn_off: ---

(define-fun RocketMachine!turn_off ((this Ref)) Ref
  (turn_off (deref-RocketMachine this))
)

; ================
; === Methods: ===
; ================

; ------------------------------------
;   Methods for class RocketMachine:
; ------------------------------------

(declare-fun RocketMachine.move (Ref Ref Ref) Ref)

(define-fun RocketMachine!move ((this Ref)(s Ref)(e Ref)) Ref
  (RocketMachine.move this s e)
)

; ===================
; === Invariants: ===
; ===================

; ------------------------------
;   Invariant for class Event:
; ------------------------------

; --- Constants: ---

(declare-const const-1-Event Event)
(declare-const const-2-Event Event)
(declare-const const-3-Event Event)



; ------------------------------
;   Invariant for class State:
; ------------------------------

; --- Constants: ---

(declare-const const-4-State State)
(declare-const const-5-State State)
(declare-const const-6-State State)



; --------------------------------------
;   Invariant for class RocketMachine:
; --------------------------------------

; --- Constants: ---

(declare-const const-7-RocketMachine RocketMachine)

; --- Invariant 1: ---

(define-fun RocketMachine.inv1 ((this Ref)) Bool
  (deref-isa-State (RocketMachine!off this))
)

(assert (! (RocketMachine.inv1 7) :named _xkassert0))

; --- Invariant 2: ---

(define-fun RocketMachine.inv2 ((this Ref)) Bool
  (deref-isa-State (RocketMachine!ready this))
)

(assert (! (RocketMachine.inv2 7) :named _xkassert1))

; --- Invariant 3: ---

(define-fun RocketMachine.inv3 ((this Ref)) Bool
  (deref-isa-State (RocketMachine!firing this))
)

(assert (! (RocketMachine.inv3 7) :named _xkassert2))

; --- Invariant 4: ---

(define-fun RocketMachine.inv4 ((this Ref)) Bool
  (deref-isa-Event (RocketMachine!turn_on this))
)

(assert (! (RocketMachine.inv4 7) :named _xkassert3))

; --- Invariant 5: ---

(define-fun RocketMachine.inv5 ((this Ref)) Bool
  (deref-isa-Event (RocketMachine!fire this))
)

(assert (! (RocketMachine.inv5 7) :named _xkassert4))

; --- Invariant 6: ---

(define-fun RocketMachine.inv6 ((this Ref)) Bool
  (deref-isa-Event (RocketMachine!turn_off this))
)

(assert (! (RocketMachine.inv6 7) :named _xkassert5))

; --- Invariant 7: ---

(define-fun RocketMachine.inv7 ((this Ref)) Bool
  (and (and (not (= (RocketMachine!off this) (RocketMachine!ready this))) (not (= (RocketMachine!off this) (RocketMachine!firing this)))) (not (= (RocketMachine!firing this) (RocketMachine!ready this))))
)

(assert (! (RocketMachine.inv7 7) :named _xkassert6))

; --- Invariant 8: ---

(define-fun RocketMachine.inv8 ((this Ref)) Bool
  (= (RocketMachine!move this (RocketMachine!off this) (RocketMachine!turn_on this)) (RocketMachine!ready this))
)

(assert (! (RocketMachine.inv8 7) :named _xkassert7))

; --- Invariant 9: ---

(define-fun RocketMachine.inv9 ((this Ref)) Bool
  (= (RocketMachine!move this (RocketMachine!ready this) (RocketMachine!fire this)) (RocketMachine!firing this))
)

(assert (! (RocketMachine.inv9 7) :named _xkassert8))

; --- Invariant 10: ---

(define-fun RocketMachine.inv10 ((this Ref)) Bool
  (= (RocketMachine!move this (RocketMachine!firing this) (RocketMachine!turn_off this)) (RocketMachine!ready this))
)

(assert (! (RocketMachine.inv10 7) :named _xkassert9))




; ======================
; === Generate heap: ===
; ======================

(assert
  (=
    heap
    (store(store(store(store(store(store(store
      ((as const (Array Ref Any)) null)
        1 (lift-Event const-1-Event))
        2 (lift-Event const-2-Event))
        3 (lift-Event const-3-Event))
        4 (lift-State const-4-State))
        5 (lift-State const-5-State))
        6 (lift-State const-6-State))
        7 (lift-RocketMachine const-7-RocketMachine))
  )
)

