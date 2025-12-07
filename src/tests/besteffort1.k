// Test: Best-effort solving with optimization
// Expected: SAT with best solution found (may be optimal or partial)

@timeout(5000)
@bestEffort
class BestEffort1 {
  x : Int
  y : Int
  z : Int

  req x >= 1
  req x <= 100
  req y >= 1
  req y <= 100
  req z >= 1
  req z <= 100

  // Some constraints
  req x + y + z <= 150
  req x * 2 <= y + z

  // Optimize
  maximize x
}

main : BestEffort1

