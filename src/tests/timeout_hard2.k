// Test case for @timeout WITH @bestEffort
// This problem is designed to be difficult enough to trigger a timeout
// With @bestEffort, we should get a partial/best result found so far

@timeout(500)  // 500ms timeout - should be too short for this problem
@bestEffort    // Return whatever solution we have when time runs out
class HardProblem2 {
  // Multiple variables with large domains
  a : Int
  b : Int
  c : Int
  d : Int
  e : Int

  // Basic bounds
  req a >= 1 && a <= 1000
  req b >= 1 && b <= 1000
  req c >= 1 && c <= 1000
  req d >= 1 && d <= 1000
  req e >= 1 && e <= 1000

  // Non-linear constraints (hard for SMT solvers)
  req a * b + c * d = e * 100
  req a * c = b * d + 50
  req (a + b) * (c + d) >= e * 10

  // Additional constraints to make it harder
  req a + b + c + d + e = 500
  req a * a + b * b < c * c + d * d
  req e > a && e > b

  // Optimization objective - try to maximize e
  maximize e
}

main : HardProblem2

