;; universe for State:
;;   State!val!0 State!val!1 State!val!2 
;; -----------
;; definitions for universe elements:
(declare-fun State!val!0 () State)
(declare-fun State!val!1 () State)
(declare-fun State!val!2 () State)
;; cardinality constraint:
(forall ((x State)) (or (= x State!val!0) (= x State!val!1) (= x State!val!2)))
;; -----------
;; universe for Event:
;;   Event!val!0 Event!val!2 Event!val!1 
;; -----------
;; definitions for universe elements:
(declare-fun Event!val!0 () Event)
(declare-fun Event!val!2 () Event)
(declare-fun Event!val!1 () Event)
;; cardinality constraint:
(forall ((x Event)) (or (= x Event!val!0) (= x Event!val!2) (= x Event!val!1)))
;; -----------
(define-fun const-6-State () State
  State!val!0)
(define-fun const-1-Event () Event
  Event!val!1)
(define-fun const-2-Event () Event
  Event!val!0)
(define-fun const-5-State () State
  State!val!2)
(define-fun const-4-State () State
  State!val!1)
(define-fun const-3-Event () Event
  Event!val!2)
(define-fun const-7-RocketMachine () RocketMachine
  (mk-RocketMachine 4 6 5 3 3 3))
(define-fun _xkassert7 () Bool
  (let ((a!1 (RocketMachine.move
             7
             (off (sel-RocketMachine (select heap 7)))
             (turn_on (sel-RocketMachine (select heap 7))))))
  (= a!1 (ready (sel-RocketMachine (select heap 7))))))
(define-fun _xkassert2 () Bool
  (let ((a!1 (select heap (firing (sel-RocketMachine (select heap 7))))))
  (is-lift-State a!1)))
(define-fun _xkassert5 () Bool
  (let ((a!1 (select heap (turn_off (sel-RocketMachine (select heap 7))))))
  (is-lift-Event a!1)))
(define-fun _xkassert9 () Bool
  (let ((a!1 (RocketMachine.move
             7
             (firing (sel-RocketMachine (select heap 7)))
             (turn_off (sel-RocketMachine (select heap 7))))))
  (= a!1 (ready (sel-RocketMachine (select heap 7))))))
(define-fun _xkassert0 () Bool
  (let ((a!1 (select heap (off (sel-RocketMachine (select heap 7))))))
  (is-lift-State a!1)))
(define-fun _xkassert8 () Bool
  (let ((a!1 (RocketMachine.move
             7
             (ready (sel-RocketMachine (select heap 7)))
             (fire (sel-RocketMachine (select heap 7))))))
  (= a!1 (firing (sel-RocketMachine (select heap 7))))))
(define-fun _xkassert3 () Bool
  (let ((a!1 (select heap (turn_on (sel-RocketMachine (select heap 7))))))
  (is-lift-Event a!1)))
(define-fun _xkassert6 () Bool
  (let ((a!1 (= (off (sel-RocketMachine (select heap 7)))
              (ready (sel-RocketMachine (select heap 7)))))
      (a!2 (= (off (sel-RocketMachine (select heap 7)))
              (firing (sel-RocketMachine (select heap 7)))))
      (a!3 (= (firing (sel-RocketMachine (select heap 7)))
              (ready (sel-RocketMachine (select heap 7))))))
  (and (not a!1) (not a!2) (not a!3))))
(define-fun _xkassert1 () Bool
  (let ((a!1 (select heap (ready (sel-RocketMachine (select heap 7))))))
  (is-lift-State a!1)))
(define-fun _xkassert4 () Bool
  (let ((a!1 (select heap (fire (sel-RocketMachine (select heap 7))))))
  (is-lift-Event a!1)))
(define-fun NULL$Ref () Int
  (- 1))
(define-fun heap () (Array Int Any)
  (let ((a!1 (store (store (store ((as const (Array Int Any)) null)
                                1
                                (lift-Event Event!val!1))
                         2
                         (lift-Event Event!val!0))
                  3
                  (lift-Event Event!val!2))))
(let ((a!2 (store (store (store a!1 4 (lift-State State!val!1))
                         5
                         (lift-State State!val!2))
                  6
                  (lift-State State!val!0))))
  (store a!2 7 (lift-RocketMachine (mk-RocketMachine 4 6 5 3 3 3))))))
(define-fun RocketMachine.move ((x!0 Int) (x!1 Int) (x!2 Int)) Int
  (ite (and (= x!0 7) (= x!1 6) (= x!2 3)) 5
    6))