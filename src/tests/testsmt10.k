
class A {
  a : Int = 1

  fun f(x : Int) : Int {
    a + x
  }

  fun g (x : Int) : Int {
    k : Int = f(x) + 1
    a + f(x) + k + 1
  }
}

class B extends A {
  b : Int = 2
 
  fun h(x : Int) : Int {
    k1 : Int = a + b
    k2 : Int = k1 + x
    g(x)
  }

  RESULT_25 : Int = h(10)
}

