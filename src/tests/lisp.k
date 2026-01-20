// @preferred_options -heapcegar-cvc5
// @expected SAT

class S_Exp {
    var text : String

}

class Atom extends S_Exp {
    req !text.contains("(") && !text.contains(")") && !text.contains(".")
}

class ListExp extends S_Exp {
    var leftExp  : S_Exp
    var rightExp : S_Exp
    req text = "(" + leftExp.text + "." + rightExp.text + ")"

    // Every S_Exp must be either an Atom or a ListExp
    req leftExp is Atom || leftExp is ListExp
    req rightExp is Atom || rightExp is ListExp
}

// Parse a simple Lisp expression
var myList : ListExp
req myList.text = "(A.B)"

--var myList2 : ListExp
--req myList2.text = "((A.B).(C.D))"


===============================================
// Expected output from Kani:
And the expected solution output from BAE:
Solution:
myList = ListExp {
   text = (A.B)
   leftExp = Atom {
      text = A
   }
   rightExp = Atom {
      text = B
   }
}
===============================================
