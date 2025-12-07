// Test: Optimization - maximize objective
// Expected: SAT with profit maximized subject to constraints

class OptTest2 {
  revenue : Int
  expenses : Int
  profit : Int
  
  req revenue >= 0
  req revenue <= 1000
  req expenses >= 0
  req expenses <= 500
  
  // Profit = revenue - expenses
  req profit = revenue - expenses
  
  // Some business constraints
  req expenses >= revenue / 5  // Need to spend to earn
  req revenue >= 100           // Minimum revenue goal
  
  // Objective: maximize profit
  maximize profit
}

main : OptTest2
