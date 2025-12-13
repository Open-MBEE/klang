// Test file for SMT Array support
// Array[K,V] maps key type K to value type V using Z3's array theory

class ArrayBasicTest {
  // Integer-keyed array with arithmetic constraints
  arr : Array[Int, Int]
  
  // Basic element constraints
  req arr[0] = 42
  req arr[1] = 100
  
  // Array values can be combined in expressions
  req arr[2] = arr[0] + arr[1]  // arr[2] = 142
}
