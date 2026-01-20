-- @expected SAT
-- Demonstrates recursive functions with match expressions
package nasa.jpl.k.fibonacci

fun fib (n: Int) : Int {
    match n with 
      case 0 => 0
      case 1 => 1
      case x => fib(x-1) + fib(x-2)
    end
}

//req C_Fib : forall x : Int :- fib(x) > 0

fib_5 : Int = fib(5)  // should be 5
fib_7 : Int = fib(7)  // should be 13
//fib_10 : Int = fib(10) // should be 55
