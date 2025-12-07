// Test file for advanced solver features in K language
// December 6, 2025

// ============================================================================
// Test 1: Optimization - minimize/maximize
// ============================================================================

class OptimizationTest {
  // A simple resource allocation problem
  
  cost : Int
  performance : Int
  memory : Int
  
  // Constraints
  req cost >= 0
  req cost <= 1000
  req performance >= 0
  req performance <= 100
  req memory >= 0
  req memory <= 64
  
  // Trade-off constraint: higher performance costs more
  req performance <= cost / 10
  
  // Memory vs performance trade-off
  req memory >= performance / 2
  
  // Optimization objective: minimize cost while meeting performance target
  req performance >= 50
  minimize cost
}

// ============================================================================
// Test 2: Multi-objective Optimization with Weights
// ============================================================================

class MultiObjectiveTest {
  energy : Int
  speed : Int
  safety : Int
  
  req energy >= 0
  req energy <= 100
  req speed >= 0
  req speed <= 200
  req safety >= 0
  req safety <= 100
  
  // Constraints
  req energy + speed <= 250  // Can't have both max energy and max speed
  req safety >= 80            // Safety is critical
  
  // Multi-objective: minimize energy, maximize speed
  // Priority weights determine importance
  minimize energy weight 1
  maximize speed weight 2
  maximize safety weight 3
}

// ============================================================================
// Test 3: Regular Expression Matching
// ============================================================================

class RegexTest {
  email : String
  phoneNumber : String
  zipCode : String
  
  // Email must match pattern (simplified)
  req email.matches("[a-z]+@[a-z]+\\.[a-z]+")
  
  // US phone number pattern
  req phoneNumber.matches("[0-9][0-9][0-9]-[0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]")
  
  // 5-digit zip code
  req zipCode.matches("[0-9][0-9][0-9][0-9][0-9]")
  
  // Constrain the actual values
  req email = "test@example.com"
  req phoneNumber = "555-123-4567"
  req zipCode = "90210"
}

// ============================================================================
// Test 4: Sequence Operations
// ============================================================================

class SeqOperationsTest {
  items : Seq[Int]
  prefix : Seq[Int]
  
  // Sequence constraints
  req items = Seq{1, 2, 3, 4, 5}
  req prefix = Seq{1, 2}
  
  // Length constraint
  itemCount : Int
  req itemCount = 5  // items.length when we add that
  
  // Prefix check would be: prefix is a prefix of items
  // req prefix.isPrefixOf(items)
}

// ============================================================================
// Test 5: Timeout and Best-Effort (annotation-based)
// ============================================================================

@timeout(5000)
@bestEffort
class TimeoutTest {
  // A computationally expensive constraint set
  x : Int
  y : Int
  z : Int
  
  req x >= 0
  req y >= 0
  req z >= 0
  
  req x + y + z = 100
  req x * y > 500
  req y * z > 500
  req x * z > 500
  
  // This should return best-effort result if timeout occurs
}

// ============================================================================
// Test 6: Soft Constraints
// ============================================================================

class SoftConstraintTest {
  budget : Int
  quality : Int
  
  // Hard constraints
  req budget >= 0
  req budget <= 100
  req quality >= 0
  req quality <= 100
  
  // Soft preference: we'd like quality >= 80, but it's not required
  @soft(10)
  req quality >= 80
  
  // Soft preference: we'd like budget <= 50
  @soft(5)
  req budget <= 50
  
  // Hard constraint that might conflict with soft ones
  req budget + quality >= 120
}

// ============================================================================
// Test 7: Opaque Function (Black-box)
// ============================================================================

class OpaqueTest {
  input : Int
  output : Int
  
  // This function would be implemented externally (e.g., in JVM)
  @opaque
  fun externalComputation(x: Int): Int
  
  req input >= 0
  req input <= 100
  
  // We want to find input such that the opaque function produces output > 50
  // The solver will use CEGAR to learn about the function behavior
  req output = externalComputation(input)
  req output > 50
}

// ============================================================================
// Test 8: Incremental Solving Scenario
// ============================================================================

@incremental
class IncrementalTest {
  // Base constraints that don't change
  base_x : Int
  base_y : Int
  
  req base_x >= 0
  req base_y >= 0
  req base_x + base_y <= 100
  
  // These could be added/removed incrementally
  scenario_a : Int
  scenario_b : Int
  
  req scenario_a = base_x * 2
  req scenario_b = base_y * 3
}

// ============================================================================
// Main test instances
// ============================================================================

optTest : OptimizationTest
multiObjTest : MultiObjectiveTest
regexTest : RegexTest
seqTest : SeqOperationsTest
timeoutTest : TimeoutTest
softTest : SoftConstraintTest
opaqueTest : OpaqueTest
incrTest : IncrementalTest
