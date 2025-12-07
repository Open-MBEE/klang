// Test case for @timeout WITH @bestEffort - easier version
// This problem should find SOME solution quickly, but may not finish optimization

@timeout(100)  // 100ms timeout - very short
@bestEffort    // Return whatever solution we have when time runs out
class PartialSolve1 {
  x : Int
  y : Int
  z : Int

  // Basic bounds - allows Z3 to find initial solution quickly
  req x >= 0 && x <= 100
  req y >= 0 && y <= 100
  req z >= 0 && z <= 100

  // Simple constraints that are easy to satisfy
  req x + y + z <= 150
  req x <= y
  req y <= z

  // Try to maximize the sum - Z3 may not find optimal before timeout
  maximize x + y + z
}

main : PartialSolve1

