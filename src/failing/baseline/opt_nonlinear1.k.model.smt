(define-fun const-1-OptNonlinear1 () OptNonlinear1
  (mk-OptNonlinear1 6))
(define-fun _xkassert1 () Bool
  (>= (x (sel-OptNonlinear1 (select heap 1))) 0))
(define-fun const-0-TopLevelDeclarations () TopLevelDeclarations
  (mk-TopLevelDeclarations 1))
(define-fun _xkassert2 () Bool
  (<= (x (sel-OptNonlinear1 (select heap 1))) 10))
(define-fun _xkassert0 () Bool
  (let ((a!1 (select heap (main (sel-TopLevelDeclarations (select heap 0))))))
  (is-lift-OptNonlinear1 a!1)))
(define-fun NULL$Ref () Int
  (- 1))
(define-fun heap () (Array Int Any)
  (store (store ((as const (Array Int Any)) null)
              0
              (lift-TopLevelDeclarations (mk-TopLevelDeclarations 1)))
       1
       (lift-OptNonlinear1 (mk-OptNonlinear1 6))))