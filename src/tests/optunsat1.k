// Test: Optimization - UNSAT case (constraints conflict with optimization direction)
// Expected: UNSAT - cannot minimize x while requiring x >= 100 with upper bound 50

@timeout(5000)
class OptUnsat1 {
  x : Int

  req x >= 0
  req x <= 50

  // This conflicts: requires x >= 100 but max is 50
  req x >= 100

  minimize x
}

main : OptUnsat1

