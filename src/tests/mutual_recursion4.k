-- @expected SAT
-- Test: Mutual recursion within a class

class Counter {
  fun countUp(n: Int) : Int {
    if n <= 0 then 0
    else 1 + countDown(n - 1)
  }
  
  fun countDown(n: Int) : Int {
    if n <= 0 then 0
    else 1 + countUp(n - 1)
  }
  
  value: Int = countUp(8)
  req value = 8
}
