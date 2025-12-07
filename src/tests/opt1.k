// Test: Optimization - minimize objective
// Expected: SAT with cost minimized to meet performance >= 50

class OptTest1 {
  cost : Int
  performance : Int
  
  req cost >= 0
  req cost <= 1000
  req performance >= 0
  req performance <= 100
  
  // Performance scales with cost
  req performance <= cost / 10
  
  // Requirement: need at least 50 performance
  req performance >= 50
  
  // Objective: minimize cost
  minimize cost
}

main : OptTest1
