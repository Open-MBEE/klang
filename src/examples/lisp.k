class S_Exp {
    var text : String

    // Every S_Exp must be either an Atom or a ListExp
    //req this is Atom || this is ListExp
}

class Atom extends S_Exp {
    var name : String
    req text = name
    //req !name.contains("(") && !name.contains(")") && !name.contains(".")
}

class ListExp extends S_Exp {
    var leftExp  : S_Exp
    var rightExp : S_Exp
    req text = "(" + leftExp.text + "." + rightExp.text + ")"
}

// Parse a simple Lisp expression
var myList : ListExp
req myList.text = "(A.B)"

--var myList2 : ListExp
--req myList2.text = "((A.((B.C).D))"


===============================================
// Expected output from Kani:
And the expected solution output from BAE:
Solution:
myList = ListExp {
   text = (A.B)
   leftExp = S_Exp {
      text = A
   }
   rightExp = S_Exp {
      text = B
   }
}
===============================================
