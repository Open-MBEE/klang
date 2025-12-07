// Test: Optimization with weights (multi-objective)
// Expected: SAT with weighted objectives

class OptTest3 {
  energy : Int
  speed : Int
  
  req energy >= 0
  req energy <= 100
  req speed >= 0
  req speed <= 100
  
  // Trade-off: can't have both max energy and max speed
  req energy + speed <= 120
  
  // Multi-objective: minimize energy (weight 1), maximize speed (weight 2)
  minimize energy weight 1
  maximize speed weight 2
}

main : OptTest3
