// Test: Timeout annotation
// Expected: SAT (should complete quickly, timeout is just a safeguard)

@timeout(10000)
class TimeoutTest1 {
  x : Int
  y : Int
  
  req x >= 0
  req x <= 100
  req y >= 0
  req y <= 100
  
  req x + y = 50
  req x > y
}

main : TimeoutTest1
